/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file, the posting lists and the term
 * lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.TermOrder;
import io.github.andreoss.search4j.core.backend.list.AllTermsList;
import io.github.andreoss.search4j.core.backend.list.TermList;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Metadata keys with a prefix, read in order from a posting list table.
 *
 * @since 0.1.0
 */
public final class VidrioMetadataKeyList implements AllTermsList {

    /**
     * Cursor over the entries.
     */
    private final TableCursor cursor;

    /**
     * Key bytes every key listed starts with.
     */
    private final byte[] prefix;

    /**
     * Current key text.
     */
    private String name;

    /**
     * Whether the list was moved.
     */
    private boolean started;

    /**
     * Whether the list is exhausted.
     */
    private boolean ended;

    /**
     * Creates a list from its parts.
     *
     * @param cursor Cursor before the first key with the prefix
     * @param prefix Key bytes every key listed starts with
     */
    private VidrioMetadataKeyList(final TableCursor cursor, final byte[] prefix) {
        this.cursor = cursor;
        this.prefix = prefix;
        this.name = "";
        this.started = false;
        this.ended = false;
    }

    /**
     * Opens the metadata keys starting with a prefix.
     *
     * @param table Posting list table
     * @param prefix Leading text, empty for every key
     * @return List before its first key
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static VidrioMetadataKeyList open(
        final TableLookup table, final String prefix
    ) {
        final byte[] bytes = MetadataKeys.prefixed(prefix);
        final TableCursor cursor = table.cursor();
        cursor.seekBefore(bytes);
        return new VidrioMetadataKeyList(cursor, bytes);
    }

    @Override
    public TermCount approxSize() {
        return new TermCount(1);
    }

    @Override
    public String term() {
        this.require();
        return this.name;
    }

    @Override
    public TermCount termFrequency() {
        throw new InvalidOperationError(
            "Term frequency is not meaningful for a metadata key list"
        );
    }

    @Override
    public TermList next() {
        this.started = true;
        if (!this.ended) {
            if (this.cursor.next() && this.matches()) {
                this.name = MetadataKeys.nameOf(this.cursor.key());
            } else {
                this.ended = true;
            }
        }
        return this;
    }

    @Override
    public TermList skipTo(final String target) {
        if (!this.ended
            && (!this.started || TermOrder.INSTANCE.compare(this.name, target) < 0)) {
            this.started = true;
            this.cursor.seekAtLeast(MetadataKeys.prefixed(target));
            if (this.cursor.afterEnd() || !this.matches()) {
                this.ended = true;
            } else {
                this.name = MetadataKeys.nameOf(this.cursor.key());
            }
        }
        return this;
    }

    @Override
    public boolean atEnd() {
        return this.ended;
    }

    private boolean matches() {
        return MetadataKeys.matches(this.cursor.key(), this.prefix);
    }

    private void require() {
        if (!this.started || this.ended) {
            throw new InvalidOperationError("Metadata key list is not on a key");
        }
    }
}
