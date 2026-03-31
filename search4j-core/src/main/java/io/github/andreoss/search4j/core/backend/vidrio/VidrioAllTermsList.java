/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file and the posting lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.list.AllTermsList;
import io.github.andreoss.search4j.core.backend.list.TermList;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.ser.SortableStrings;
import io.github.andreoss.search4j.core.types.TermCount;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * Term list over every term of a committed posting list table, read from
 * the keys of the first chunks.
 *
 * <p>Continuation chunks and the document lengths are skipped. The term
 * frequency is read from the chunk when asked for and kept until the
 * next move. With a prefix, the list ends at the first term without it.
 * A skip never moves backwards.</p>
 *
 * @since 0.1.0
 */
public final class VidrioAllTermsList implements AllTermsList {

    /**
     * Key before every term key and after the document lengths.
     */
    private static final byte[] TERMS = {0x00, (byte) 0xFF};

    /**
     * Cursor over the table.
     */
    private final TableCursor cursor;

    /**
     * Number of entries of the table.
     */
    private final EntryCount entries;

    /**
     * Prefix the terms are restricted to.
     */
    private final byte[] prefix;

    /**
     * Bytes of the current term.
     */
    private byte[] name;

    /**
     * Term frequency of the current term, 0 until read.
     */
    private TermCount freq;

    /**
     * Whether the list has moved onto its first term.
     */
    private boolean started;

    /**
     * Whether the list has moved past its last term.
     */
    private boolean ended;

    /**
     * Creates the list from its parts.
     *
     * @param cursor Cursor over the table, unpositioned
     * @param entries Number of entries of the table
     * @param prefix Prefix the terms are restricted to, not copied
     */
    private VidrioAllTermsList(
        final TableCursor cursor, final EntryCount entries, final byte[] prefix
    ) {
        this.cursor = cursor;
        this.entries = entries;
        this.prefix = prefix;
        this.name = new byte[0];
        this.freq = TermCount.ZERO;
        this.started = false;
        this.ended = false;
    }

    /**
     * Opens the list over a table.
     *
     * @param table Posting list table
     * @param entries Number of entries of the table
     * @param prefix Prefix the terms are restricted to, empty for all
     * @return List before its first term
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static VidrioAllTermsList open(
        final TableLookup table, final EntryCount entries, final String prefix
    ) {
        return new VidrioAllTermsList(
            table.cursor(), entries, PostListKeys.bytes(prefix)
        );
    }

    @Override
    public TermCount approxSize() {
        return new TermCount(
            Math.toIntExact(Math.min(this.entries.value(), Integer.MAX_VALUE))
        );
    }

    @Override
    public String term() {
        this.require();
        return new String(this.name, StandardCharsets.UTF_8);
    }

    @Override
    public TermCount termFrequency() {
        this.require();
        if (this.freq.value() == 0) {
            this.freq = FirstHeader.read(ByteReader.over(this.cursor.tag()))
                .freqs().termFrequency();
        }
        return this.freq;
    }

    @Override
    public TermList next() {
        this.freq = TermCount.ZERO;
        if (this.started) {
            this.advance();
        } else {
            this.started = true;
            this.seek(this.prefix, true);
        }
        return this;
    }

    @Override
    public TermList skipTo(final String target) {
        this.freq = TermCount.ZERO;
        final byte[] want = PostListKeys.bytes(target);
        if (!this.ended
            && (!this.started || Arrays.compareUnsigned(this.name, want) < 0)) {
            this.started = true;
            this.seek(want, false);
        }
        return this;
    }

    @Override
    public boolean atEnd() {
        return this.ended;
    }

    private void advance() {
        final boolean moved = !this.ended && this.cursor.next();
        if (moved) {
            this.scan();
        } else {
            this.ended = true;
        }
    }

    private void seek(final byte[] want, final boolean skip) {
        if (want.length == 0) {
            this.cursor.seekAtLeast(VidrioAllTermsList.TERMS);
            this.settle(true);
        } else if (this.cursor.seekAtLeast(PostListKeys.first(want))) {
            this.name = want.clone();
            this.trim();
        } else {
            this.settle(skip);
        }
    }

    private void settle(final boolean skip) {
        if (this.cursor.afterEnd()) {
            this.ended = true;
        } else if (skip) {
            this.scan();
        } else {
            this.name = SortableStrings.unpack(
                ByteBuffer.wrap(this.cursor.key().data())
            );
            this.trim();
        }
    }

    private void scan() {
        boolean more = true;
        while (more) {
            final Optional<byte[]> term =
                VidrioAllTermsList.termOf(this.cursor.key().data());
            if (term.isPresent()) {
                this.name = term.get();
                this.trim();
                more = false;
            } else if (!this.cursor.next()) {
                this.ended = true;
                more = false;
            }
        }
    }

    private void trim() {
        if (!Arrays.equals(
            this.name, 0, Math.min(this.name.length, this.prefix.length),
            this.prefix, 0, this.prefix.length
        )) {
            this.ended = true;
        }
    }

    private void require() {
        if (!this.started || this.ended) {
            throw new InvalidOperationError("Term list is not on a term");
        }
    }

    private static Optional<byte[]> termOf(final byte[] key) {
        int nul = 0;
        while (nul < key.length && key[nul] != 0) {
            nul = nul + 1;
        }
        Optional<byte[]> result = Optional.empty();
        if (nul == key.length) {
            result = Optional.of(key);
        } else if (nul + 1 < key.length && key[nul + 1] == (byte) 0xFF) {
            final ByteBuffer buf = ByteBuffer.wrap(key);
            result = Optional.of(SortableStrings.unpack(buf))
                .filter(term -> !buf.hasRemaining());
        }
        return result;
    }
}
