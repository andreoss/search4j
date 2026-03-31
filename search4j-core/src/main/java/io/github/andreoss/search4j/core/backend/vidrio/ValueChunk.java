/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file and the posting lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.types.DocId;
import java.nio.charset.StandardCharsets;

/**
 * Reader of one value chunk: the value of the first document, then for
 * each later document its distance one below and its length-prefixed
 * value.
 *
 * @since 0.1.0
 */
final class ValueChunk {

    /**
     * Bytes of the chunk.
     */
    private final ByteReader reader;

    /**
     * Current document.
     */
    private DocId doc;

    /**
     * Current value.
     */
    private String text;

    /**
     * Whether the chunk is exhausted.
     */
    private boolean ended;

    /**
     * Creates a reader from its parts.
     *
     * @param reader Bytes of the chunk after the first value
     * @param doc First document
     * @param text Value of the first document
     * @param ended Whether the chunk is exhausted
     */
    private ValueChunk(
        final ByteReader reader, final DocId doc, final String text,
        final boolean ended
    ) {
        this.reader = reader;
        this.doc = doc;
        this.text = text;
        this.ended = ended;
    }

    /**
     * Opens a chunk on its first document.
     *
     * @param data Chunk bytes
     * @param first First document of the chunk
     * @return Reader on the first document
     */
    static ValueChunk of(final byte[] data, final DocId first) {
        final ByteReader reader = ByteReader.over(data);
        return new ValueChunk(
            reader, first, ValueChunk.decode(reader.string("First value")),
            false
        );
    }

    /**
     * A chunk already exhausted.
     *
     * @return Reader at its end
     */
    static ValueChunk none() {
        return new ValueChunk(
            ByteReader.over(new byte[0]), DocId.NONE, "", true
        );
    }

    /**
     * Tests whether the chunk is exhausted.
     *
     * @return True once moved past the last document
     */
    boolean atEnd() {
        return this.ended;
    }

    /**
     * Current document.
     *
     * @return Document
     */
    DocId docId() {
        return this.doc;
    }

    /**
     * Current value.
     *
     * @return Value
     */
    String value() {
        return this.text;
    }

    /**
     * Moves to the next document.
     */
    void next() {
        if (this.reader.done()) {
            this.ended = true;
        } else {
            this.doc = this.step();
            this.text = ValueChunk.decode(this.reader.string("Streamed value"));
        }
    }

    /**
     * Moves to the first document at or after a target, never backwards.
     *
     * @param target Lowest document wanted
     */
    void skipTo(final DocId target) {
        if (!this.ended && target.compareTo(this.doc) > 0) {
            boolean searching = true;
            while (searching && !this.reader.done()) {
                this.doc = this.step();
                final byte[] bytes = this.reader.string("Streamed value");
                if (this.doc.compareTo(target) >= 0) {
                    this.text = ValueChunk.decode(bytes);
                    searching = false;
                }
            }
            this.ended = searching;
        }
    }

    private DocId step() {
        final long next = this.doc.value()
            + this.reader.narrow("Streamed value document") + 1L;
        if (next > Integer.MAX_VALUE) {
            throw new DatabaseCorruptError(
                "Document number out of range in value chunk"
            );
        }
        return DocId.of((int) next);
    }

    private static String decode(final byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
