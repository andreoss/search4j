/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table and the version file.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Postings of one chunk, read forward: the frequency of the first
 * document, then for every later document the gap to it, one below the
 * distance, and its frequency.
 *
 * @since 0.1.0
 */
final class PostingChunk {

    /**
     * Name of the frequency field.
     */
    private static final String WDF = "Within-document frequency";

    /**
     * Reader over the postings.
     */
    private final ByteReader data;

    /**
     * Current document.
     */
    private DocId doc;

    /**
     * Frequency in the current document.
     */
    private TermCount wdf;

    /**
     * Whether every posting was read.
     */
    private boolean ended;

    /**
     * Creates a chunk from its parts.
     *
     * @param data Reader over the postings
     * @param doc Current document
     * @param wdf Frequency in the current document
     * @param ended Whether every posting was read
     */
    private PostingChunk(
        final ByteReader data, final DocId doc, final TermCount wdf,
        final boolean ended
    ) {
        this.data = data;
        this.doc = doc;
        this.wdf = wdf;
        this.ended = ended;
    }

    /**
     * Opens a chunk on its first posting.
     *
     * @param first First document of the chunk
     * @param data Reader after the chunk header
     * @return Chunk, ended when there are no postings
     */
    static PostingChunk of(final DocId first, final ByteReader data) {
        final boolean ended = data.done();
        TermCount wdf = TermCount.ZERO;
        if (!ended) {
            wdf = PostingFields.count(data, PostingChunk.WDF);
        }
        return new PostingChunk(data, first, wdf, ended);
    }

    /**
     * A chunk without postings.
     *
     * @return Ended chunk
     */
    static PostingChunk none() {
        return PostingChunk.of(DocId.NONE, ByteReader.over(new byte[0]));
    }

    /**
     * Current document.
     *
     * @return Document
     */
    DocId doc() {
        return this.doc;
    }

    /**
     * Frequency in the current document.
     *
     * @return Frequency
     */
    TermCount wdf() {
        return this.wdf;
    }

    /**
     * Tests whether every posting was read.
     *
     * @return True once moved past the last posting
     */
    boolean ended() {
        return this.ended;
    }

    /**
     * Moves to the next posting.
     *
     * @return False when there is none, the chunk then ended
     */
    boolean next() {
        final boolean more = !this.data.done();
        if (more) {
            this.doc = this.gap();
            this.wdf = PostingFields.count(this.data, PostingChunk.WDF);
        } else {
            this.ended = true;
        }
        return more;
    }

    /**
     * Moves forward to the first posting at or after a document.
     *
     * @param target Document wanted
     * @return True when reached; false when the postings ran out, the
     *  document and frequency then stale
     */
    boolean forward(final DocId target) {
        boolean found = this.doc.compareTo(target) >= 0;
        while (!found && !this.data.done()) {
            this.doc = this.gap();
            final TermCount next = PostingFields.count(this.data, PostingChunk.WDF);
            found = this.doc.compareTo(target) >= 0;
            if (found) {
                this.wdf = next;
            }
        }
        return found;
    }

    /**
     * Skips the postings left.
     */
    void drain() {
        this.data.rest();
    }

    private DocId gap() {
        return PostingFields.after(
            this.doc, this.data.narrow("Document gap") + 1L, "Document"
        );
    }
}
