/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file and the posting lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.error.SerialisationError;
import io.github.andreoss.search4j.core.ser.BitReader;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;

/**
 * Position list decoded on demand from a tag of the position table.
 *
 * <p>The list starts before its first position; positions are decoded as
 * the list moves. An empty list is at its end from the first move.</p>
 *
 * @since 0.1.0
 */
public final class VidrioPositionList implements PositionList {

    /**
     * Decoder of the positions after the first.
     */
    private final BitReader reader;

    /**
     * Last position.
     */
    private final TermPos last;

    /**
     * Number of positions.
     */
    private final TermCount size;

    /**
     * Current position, the first before the first move.
     */
    private TermPos current;

    /**
     * Whether the list has moved onto its first position.
     */
    private boolean started;

    /**
     * Whether the list has moved past its last position.
     */
    private boolean ended;

    /**
     * Creates the list from its decoded parts.
     *
     * @param reader Decoder of the positions after the first, started
     * @param first First position
     * @param last Last position
     * @param size Number of positions
     */
    VidrioPositionList(
        final BitReader reader, final TermPos first, final TermPos last,
        final TermCount size
    ) {
        this.reader = reader;
        this.current = first;
        this.last = last;
        this.size = size;
        this.started = false;
        this.ended = false;
    }

    /**
     * Decodes a tag of the position table.
     *
     * @param data Tag bytes, empty for no positions
     * @return List before its first position
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static VidrioPositionList of(final byte[] data) {
        return PositionTag.decode(data);
    }

    /**
     * Tests whether the list is exhausted.
     *
     * @return True once moved past the last position
     */
    public boolean atEnd() {
        return this.ended;
    }

    @Override
    public TermCount approxSize() {
        return this.size;
    }

    @Override
    public TermPos back() {
        return this.last;
    }

    @Override
    public TermPos position() {
        if (!this.started || this.ended) {
            throw new InvalidOperationError("Position list is not on a position");
        }
        return this.current;
    }

    @Override
    public boolean next() {
        if (this.started) {
            this.advance();
        } else {
            this.started = true;
            this.ended = this.size.value() == 0;
        }
        return !this.ended;
    }

    @Override
    public boolean skipTo(final TermPos target) {
        this.started = true;
        if (this.size.value() == 0) {
            this.ended = true;
        } else if (target.compareTo(this.last) >= 0) {
            this.ended = !target.equals(this.last);
            if (!this.ended) {
                this.current = this.last;
            }
        } else {
            while (this.current.compareTo(target) < 0) {
                this.current = this.decodeNext();
            }
            this.ended = false;
        }
        return !this.ended;
    }

    private void advance() {
        final boolean more = !this.ended && !this.current.equals(this.last);
        if (more) {
            this.current = this.decodeNext();
        } else {
            this.ended = true;
        }
    }

    private TermPos decodeNext() {
        final long value;
        try {
            value = this.reader.decodeInterpolativeNext();
        } catch (final SerialisationError ex) {
            final DatabaseCorruptError error = new DatabaseCorruptError(
                String.format("Position list data corrupt after %s", this.current)
            );
            error.initCause(ex);
            throw error;
        }
        return new TermPos((int) value);
    }
}
