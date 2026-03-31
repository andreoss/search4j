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

import io.github.andreoss.search4j.core.backend.list.ValueList;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.Optional;

/**
 * Values of one slot across documents, read chunk by chunk from a posting
 * list table.
 *
 * @since 0.1.0
 */
public final class VidrioValueList implements ValueList {

    /**
     * Cursor over the chunks.
     */
    private final TableCursor cursor;

    /**
     * Slot read.
     */
    private final ValueNo number;

    /**
     * Current chunk.
     */
    private ValueChunk reader;

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
     * @param cursor Cursor over the chunks
     * @param number Slot read
     * @param reader Current chunk, none yet
     */
    private VidrioValueList(
        final TableCursor cursor, final ValueNo number, final ValueChunk reader
    ) {
        this.cursor = cursor;
        this.number = number;
        this.reader = reader;
        this.started = false;
        this.ended = false;
    }

    /**
     * Opens the values of a slot.
     *
     * @param table Posting list table holding the chunks
     * @param slot Slot read
     * @return List before its first value
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static VidrioValueList of(final TableLookup table, final ValueNo slot) {
        return new VidrioValueList(table.cursor(), slot, ValueChunk.none());
    }

    @Override
    public DocId docId() {
        this.require();
        return this.reader.docId();
    }

    @Override
    public String value() {
        this.require();
        return this.reader.value();
    }

    @Override
    public ValueNo slot() {
        return this.number;
    }

    @Override
    public boolean atEnd() {
        return this.ended;
    }

    @Override
    public void next() {
        if (!this.ended && this.started) {
            this.reader.next();
            this.step();
        } else if (!this.ended) {
            this.started = true;
            this.cursor.seekAtLeast(ValueKeys.chunk(this.number, DocId.of(1)));
            this.settle();
        }
    }

    @Override
    public void skipTo(final DocId target) {
        if (!this.ended && !this.within(target)) {
            this.seek(target);
        }
    }

    @Override
    public boolean check(final DocId target) {
        boolean result = false;
        if (!this.ended) {
            result = this.within(target) || this.probe(target);
        }
        return result;
    }

    @Override
    public String description() {
        return String.format("VidrioValueList(slot=%s)", this.number);
    }

    private boolean within(final DocId target) {
        boolean result = false;
        if (this.started && !this.reader.atEnd()) {
            result = this.skip(target);
        }
        this.started = true;
        return result;
    }

    private void seek(final DocId target) {
        if (this.cursor.seek(ValueKeys.chunk(this.number, target))) {
            this.settle();
        } else if (!this.load() || !this.skip(target)) {
            this.cursor.next();
            this.settle();
        }
    }

    private boolean probe(final DocId target) {
        final boolean result;
        if (this.cursor.seek(ValueKeys.chunk(this.number, target))) {
            this.load();
            result = true;
        } else {
            result = this.load() && this.skip(target);
        }
        return result;
    }

    private boolean skip(final DocId target) {
        this.reader.skipTo(target);
        return !this.reader.atEnd();
    }

    private void step() {
        if (this.reader.atEnd()) {
            this.cursor.next();
            this.settle();
        }
    }

    private void settle() {
        if (this.cursor.afterEnd() || !this.load()) {
            this.ended = true;
        }
    }

    private boolean load() {
        final Optional<DocId> first =
            ValueKeys.chunkStart(this.number, this.cursor.key());
        if (first.isPresent()) {
            this.reader = ValueChunk.of(this.cursor.tag(), first.get());
        }
        return first.isPresent();
    }

    private void require() {
        if (!this.started || this.ended || this.reader.atEnd()) {
            throw new InvalidOperationError("Value list is not on a document");
        }
    }
}
