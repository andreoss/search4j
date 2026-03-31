/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table and the version file.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.SerialisationError;
import io.github.andreoss.search4j.core.ser.SortableStrings;
import io.github.andreoss.search4j.core.ser.SortableUints;
import io.github.andreoss.search4j.core.types.DocId;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * A posting list key taken apart: the term and the first document of the
 * chunk, absent for a first chunk.
 *
 * @since 0.1.0
 */
final class ChunkKey {

    /**
     * Term bytes.
     */
    private final byte[] term;

    /**
     * First document of the chunk, none for a first chunk.
     */
    private final DocId start;

    /**
     * Whether the key was empty.
     */
    private final boolean blank;

    /**
     * Creates a key from its parts.
     *
     * @param term Term bytes, not copied
     * @param start First document of the chunk, none for a first chunk
     * @param blank Whether the key was empty
     */
    private ChunkKey(final byte[] term, final DocId start, final boolean blank) {
        this.term = term;
        this.start = start;
        this.blank = blank;
    }

    /**
     * Takes a key apart.
     *
     * @param key Key of a posting list entry
     * @return Its parts
     */
    static ChunkKey of(final ItemKey key) {
        final byte[] data = key.data();
        final ByteBuffer buf = ByteBuffer.wrap(data);
        final int prefix = PostListKeys.lengthsPrefix(data);
        final byte[] term;
        if (prefix > 0) {
            buf.position(prefix);
            term = new byte[0];
        } else {
            term = SortableStrings.unpack(buf);
        }
        DocId start = DocId.NONE;
        if (buf.hasRemaining()) {
            start = ChunkKey.document(buf, key);
        }
        return new ChunkKey(term, start, data.length == 0);
    }

    /**
     * Tests whether the key belongs to a term.
     *
     * @param name Term bytes
     * @return True for a chunk of the term
     */
    boolean matches(final byte[] name) {
        return !this.blank && Arrays.equals(this.term, name);
    }

    /**
     * Tests whether the key is of a first chunk.
     *
     * @return True without a document number
     */
    boolean first() {
        return !this.start.valid();
    }

    /**
     * First document of the chunk.
     *
     * @return Document, a corrupt-data error for a first chunk
     */
    DocId start() {
        if (this.first()) {
            throw new DatabaseCorruptError(
                "Posting list chunk key without a document number"
            );
        }
        return this.start;
    }

    private static DocId document(final ByteBuffer buf, final ItemKey key) {
        final long number;
        try {
            number = SortableUints.unpack(buf);
        } catch (final SerialisationError ex) {
            final DatabaseCorruptError error = new DatabaseCorruptError(
                String.format("Bad document number in key %s", key)
            );
            error.initCause(ex);
            throw error;
        }
        if (number < 1L || number > Integer.MAX_VALUE) {
            throw new DatabaseCorruptError(
                String.format("Document number out of range in key %s", key)
            );
        }
        return DocId.of((int) number);
    }
}
