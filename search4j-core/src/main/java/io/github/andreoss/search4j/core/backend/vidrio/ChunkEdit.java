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

import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Edit of one chunk: the postings read from it, the writer replacing it
 * and the last document the writer may hold.
 *
 * @since 0.1.0
 */
final class ChunkEdit {

    /**
     * Last document of a chunk that is the last of its list.
     */
    static final long UNBOUNDED = Long.MAX_VALUE;

    /**
     * Postings of the chunk replaced, ended once copied.
     */
    private final PostingChunk source;

    /**
     * Writer of the chunk.
     */
    private final ChunkWriter target;

    /**
     * Last document the writer may hold.
     */
    private final long max;

    /**
     * Creates an edit from its parts.
     *
     * @param source Postings of the chunk replaced
     * @param target Writer of the chunk
     * @param max Last document the writer may hold
     */
    ChunkEdit(
        final PostingChunk source, final ChunkWriter target, final long max
    ) {
        this.source = source;
        this.target = target;
        this.max = max;
    }

    /**
     * Last document the writer may hold.
     *
     * @return Document number
     */
    long max() {
        return this.max;
    }

    /**
     * Tests whether every posting of the chunk replaced was copied.
     *
     * @return True once the source ended
     */
    boolean exhausted() {
        return this.source.ended();
    }

    /**
     * Copies the postings before a document and drops its own.
     *
     * @param doc Document being changed
     */
    void reach(final DocId doc) {
        boolean more = !this.source.ended();
        while (more) {
            final int order = this.source.doc().compareTo(doc);
            if (order < 0) {
                this.target.append(this.source.doc(), this.source.wdf());
                more = this.source.next();
            } else {
                if (order == 0) {
                    this.source.next();
                }
                more = false;
            }
        }
    }

    /**
     * Appends a posting.
     *
     * @param doc Document identifier
     * @param wdf Frequency in the document
     */
    void append(final DocId doc, final TermCount wdf) {
        this.target.append(doc, wdf);
    }

    /**
     * Writes the chunk.
     */
    void flush() {
        this.target.flush();
    }

    /**
     * Copies the postings left and writes the chunk.
     */
    void finish() {
        while (!this.source.ended()) {
            this.target.append(this.source.doc(), this.source.wdf());
            this.source.next();
        }
        this.target.flush();
    }
}
