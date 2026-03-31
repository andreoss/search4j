/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file and the posting lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.InvariantError;
import io.github.andreoss.search4j.core.ser.Varints;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Writer merging changes into the value chunks of one slot.
 *
 * <p>Changes come in document order. The chunk holding a document is read,
 * its entries copied over around the change, and written back when the
 * next change belongs to a later chunk or on {@link #finish}; a chunk
 * grown to the threshold is written out and a new one started. A chunk
 * whose first document changed is filed under its new key, an emptied
 * one removed.</p>
 *
 * @since 0.1.0
 */
final class ValueChunkWriter {

    /**
     * Table written.
     */
    private final TableWriter table;

    /**
     * Slot written.
     */
    private final ValueNo slot;

    /**
     * Entries of the chunk being built.
     */
    private final ByteArrayOutputStream tag;

    /**
     * Entries of the chunk read, not yet copied.
     */
    private ValueChunk reader;

    /**
     * Last document appended.
     */
    private DocId previous;

    /**
     * First document of the chunk read, none when no chunk was read.
     */
    private DocId origin;

    /**
     * First document of the chunk being built, none while empty.
     */
    private DocId start;

    /**
     * Last document the chunk may hold, 0 when no chunk is open.
     */
    private long limit;

    /**
     * Creates a writer from its parts.
     *
     * @param table Table written
     * @param slot Slot written
     * @param reader Entries of the chunk read, none yet
     */
    private ValueChunkWriter(
        final TableWriter table, final ValueNo slot, final ValueChunk reader
    ) {
        this.table = table;
        this.slot = slot;
        this.tag = new ByteArrayOutputStream();
        this.reader = reader;
        this.previous = DocId.NONE;
        this.origin = DocId.NONE;
        this.start = DocId.NONE;
        this.limit = 0L;
    }

    /**
     * Creates a writer over a slot.
     *
     * @param table Table written
     * @param slot Slot written
     * @return Writer before any change
     */
    static ValueChunkWriter over(final TableWriter table, final ValueNo slot) {
        return new ValueChunkWriter(table, slot, ValueChunk.none());
    }

    /**
     * Sets or removes the value of a document.
     *
     * @param doc Document, after every document changed so far
     * @param value Value, empty to remove
     */
    void update(final DocId doc, final String value) {
        if (this.limit != 0L && doc.value() > this.limit) {
            this.finish();
            this.limit = 0L;
        }
        if (this.limit == 0L) {
            this.open(doc);
        }
        while (!this.reader.atEnd() && this.reader.docId().compareTo(doc) < 0) {
            this.copy();
        }
        if (!this.reader.atEnd() && this.reader.docId().equals(doc)) {
            this.reader.next();
        }
        if (!value.isEmpty()) {
            this.append(doc, value);
        }
    }

    /**
     * Copies the entries left of the chunk read and writes the chunk.
     */
    void finish() {
        while (!this.reader.atEnd()) {
            this.copy();
        }
        this.write();
    }

    private void open(final DocId doc) {
        this.limit = Integer.MAX_VALUE;
        this.start = DocId.NONE;
        final TableCursor cursor = this.table.cursor();
        if (cursor.seek(ValueKeys.chunk(this.slot, doc))) {
            this.origin = doc;
        } else {
            this.origin = ValueKeys.chunkStart(this.slot, cursor.key())
                .orElse(DocId.NONE);
        }
        if (this.origin.valid()) {
            this.reader = ValueChunk.of(cursor.tag(), this.origin);
        }
        if (cursor.next()) {
            ValueKeys.chunkStart(this.slot, cursor.key()).ifPresent(
                next -> this.limit = next.value() - 1L
            );
        }
    }

    private void copy() {
        this.append(this.reader.docId(), this.reader.value());
        this.reader.next();
    }

    private void append(final DocId doc, final String value) {
        if (this.tag.size() == 0) {
            this.start = doc;
        } else {
            if (doc.compareTo(this.previous) <= 0) {
                throw new InvariantError("Values appended out of order");
            }
            this.tag.writeBytes(
                Varints.pack((long) doc.value() - this.previous.value() - 1L)
            );
        }
        this.previous = doc;
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        this.tag.writeBytes(Varints.pack(bytes.length));
        this.tag.writeBytes(bytes);
        if (this.tag.size() >= 2000) {
            this.write();
        }
    }

    private void write() {
        if (this.origin.valid() && !this.start.equals(this.origin)) {
            this.table.delete(ValueKeys.chunk(this.slot, this.origin));
        }
        if (this.tag.size() > 0) {
            this.table.add(
                ValueKeys.chunk(this.slot, this.start), this.tag.toByteArray()
            );
        }
        this.origin = DocId.NONE;
        this.tag.reset();
    }
}
