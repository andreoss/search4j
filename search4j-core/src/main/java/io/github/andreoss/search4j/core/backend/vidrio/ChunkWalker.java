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

import io.github.andreoss.search4j.core.backend.TermFrequencies;
import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Walk over the chunks of one posting list through a table cursor.
 *
 * <p>The walk starts before the first posting. Chunks are read as the
 * walk reaches them, and a skip finds its chunk by its key.</p>
 *
 * @since 0.1.0
 */
final class ChunkWalker {

    /**
     * Message of a chunk whose postings end before its last document.
     */
    private static final String SHORT =
        "Posting list chunk ends before its last document";

    /**
     * Cursor over the table.
     */
    private final TableCursor cursor;

    /**
     * Term bytes.
     */
    private final byte[] term;

    /**
     * Frequencies of the term.
     */
    private final TermFrequencies freqs;

    /**
     * Upper bound on the within-document frequency.
     */
    private final TermCount bound;

    /**
     * Whether the list is absent.
     */
    private final boolean empty;

    /**
     * Chunk the walk is in.
     */
    private Loaded span;

    /**
     * Whether the walk has moved onto its first posting.
     */
    private boolean started;

    /**
     * Whether the walk has moved past the last posting.
     */
    private boolean ended;

    /**
     * Creates a walk from its parts.
     *
     * @param cursor Cursor over the table, on the first chunk
     * @param term Term bytes, not copied
     * @param freqs Frequencies of the term
     * @param bound Upper bound on the within-document frequency
     * @param empty Whether the list is absent
     * @param span Chunk the walk is in
     * @param ended Whether the walk has moved past the last posting
     */
    private ChunkWalker(
        final TableCursor cursor, final byte[] term,
        final TermFrequencies freqs, final TermCount bound,
        final boolean empty, final Loaded span, final boolean ended
    ) {
        this.cursor = cursor;
        this.term = term;
        this.freqs = freqs;
        this.bound = bound;
        this.empty = empty;
        this.span = span;
        this.ended = ended;
    }

    /**
     * Opens the walk over a term.
     *
     * @param cursor Cursor over the table, taken over
     * @param term Term bytes, empty for the document lengths
     * @return Walk before the first posting
     */
    static ChunkWalker open(final TableCursor cursor, final byte[] term) {
        final ChunkWalker walker;
        if (cursor.exact(PostListKeys.first(term))) {
            final ByteReader reader = ByteReader.over(cursor.tag());
            final FirstHeader head = FirstHeader.read(reader);
            final Loaded loaded = ChunkWalker.load(head.first(), reader);
            TermCount bound = TermCount.ZERO;
            if (term.length > 0) {
                final TermCount wdf = loaded.postings().wdf();
                bound = Collections.max(
                    List.of(head.freqs().collection().minus(wdf), wdf)
                );
            }
            walker = new ChunkWalker(
                cursor, term.clone(), head.freqs(), bound, false, loaded, false
            );
        } else {
            walker = new ChunkWalker(
                cursor, term.clone(), TermFrequencies.NONE, TermCount.ZERO,
                true, Loaded.none(), true
            );
        }
        return walker;
    }

    /**
     * Frequencies of the term.
     *
     * @return Frequencies, none for an absent list
     */
    TermFrequencies freqs() {
        return this.freqs;
    }

    /**
     * Upper bound on the within-document frequency.
     *
     * @return Bound, 0 for the document lengths
     */
    TermCount bound() {
        return this.bound;
    }

    /**
     * Tests whether the walk is past the last posting.
     *
     * @return True at the end
     */
    boolean ended() {
        return this.ended;
    }

    /**
     * Current document.
     *
     * @return Document identifier
     */
    DocId doc() {
        this.require();
        return this.span.postings().doc();
    }

    /**
     * Frequency in the current document.
     *
     * @return Within-document frequency
     */
    TermCount wdf() {
        this.require();
        return this.span.postings().wdf();
    }

    /**
     * Moves to the next posting.
     */
    void next() {
        if (this.started) {
            if (!this.ended && !this.span.postings().next()) {
                this.nextChunk();
            }
        } else {
            this.started = true;
        }
    }

    /**
     * Moves to the first posting at or after a document, never backwards.
     *
     * @param target Document wanted
     */
    void skipTo(final DocId target) {
        this.started = true;
        if (!this.ended && target.compareTo(this.span.postings().doc()) > 0) {
            if (!this.contains(target)) {
                this.moveTo(target);
            }
            if (!this.ended && !this.forward(target)) {
                throw new DatabaseCorruptError(ChunkWalker.SHORT);
            }
        }
    }

    /**
     * Moves to a document, backwards if need be.
     *
     * @param target Document wanted
     * @return True when the document is in the list
     */
    boolean jumpTo(final DocId target) {
        this.started = true;
        boolean found = false;
        if (!this.empty) {
            boolean ready = true;
            if (this.ended || !this.contains(target)
                || target.compareTo(this.span.postings().doc()) < 0) {
                this.ended = false;
                this.moveTo(target);
                ready = !this.ended;
            }
            found = ready && this.forward(target)
                && target.equals(this.span.postings().doc());
        }
        return found;
    }

    /**
     * Narrows the range of documents the list can return.
     *
     * @param whole Range already known
     * @return Range from the first document of the current chunk, to its
     *  last when it is the last chunk; an empty range for an absent list
     */
    DocRange range(final DocRange whole) {
        final DocRange result;
        if (this.empty) {
            result = new DocRange(whole.first(), DocId.NONE);
        } else if (this.span.last()) {
            result = new DocRange(this.span.first(), this.span.end());
        } else {
            result = new DocRange(this.span.first(), whole.last());
        }
        return result;
    }

    private void require() {
        if (!this.started || this.ended) {
            throw new InvalidOperationError("Posting list is not on a document");
        }
    }

    private boolean contains(final DocId target) {
        return target.compareTo(this.span.first()) >= 0
            && target.compareTo(this.span.end()) <= 0;
    }

    private boolean forward(final DocId target) {
        final PostingChunk postings = this.span.postings();
        boolean reached = postings.doc().compareTo(target) >= 0;
        if (!reached && target.compareTo(this.span.end()) <= 0) {
            reached = postings.forward(target);
            if (!reached) {
                throw new DatabaseCorruptError(ChunkWalker.SHORT);
            }
        } else if (!reached) {
            postings.drain();
        }
        return reached;
    }

    private void moveTo(final DocId target) {
        this.cursor.seek(PostListKeys.chunk(this.term, target));
        final ChunkKey key = ChunkKey.of(this.cursor.key());
        if (key.matches(this.term)) {
            this.ended = false;
            final ByteReader reader = ByteReader.over(this.cursor.tag());
            final DocId start;
            if (key.first()) {
                start = FirstHeader.read(reader).first();
            } else {
                start = key.start();
            }
            this.span = ChunkWalker.load(start, reader);
            if (target.compareTo(this.span.end()) > 0) {
                this.nextChunk();
            }
        } else {
            this.ended = true;
        }
    }

    private void nextChunk() {
        if (this.span.last()) {
            this.ended = true;
        } else {
            this.span = ChunkWalker.load(
                this.following(), ByteReader.over(this.cursor.tag())
            );
        }
    }

    private DocId following() {
        if (!this.cursor.next()) {
            throw this.finished();
        }
        final ChunkKey key = ChunkKey.of(this.cursor.key());
        if (!key.matches(this.term)) {
            throw this.finished();
        }
        final DocId start = key.start();
        final DocId last = this.span.postings().doc();
        if (start.compareTo(last) <= 0) {
            throw new DatabaseCorruptError(
                String.format(
                    "Document ID in new chunk of postlist (%s) is not greater than final document ID in previous chunk (%s)",
                    start, last
                )
            );
        }
        return start;
    }

    private DatabaseCorruptError finished() {
        this.ended = true;
        return new DatabaseCorruptError(
            String.format(
                "Unexpected end of posting list for '%s'",
                new String(this.term, StandardCharsets.UTF_8)
            )
        );
    }

    private static Loaded load(final DocId start, final ByteReader reader) {
        final ChunkHeader head = ChunkHeader.read(reader, start);
        final PostingChunk postings = PostingChunk.of(start, reader);
        if (postings.ended()) {
            throw new DatabaseCorruptError(
                "Data ran out unexpectedly when reading posting list"
            );
        }
        return new Loaded(postings, start, head.end(), head.last());
    }

    /**
     * A chunk the walk is in.
     *
     * @param postings Its postings
     * @param first Its first document
     * @param end Its last document
     * @param last Whether it is the last chunk of the list
     * @since 0.1.0
     */
    private record Loaded(
        PostingChunk postings, DocId first, DocId end, boolean last
    ) {

        /**
         * Chunk of an absent list.
         *
         * @return Ended chunk without documents
         */
        static Loaded none() {
            return new Loaded(PostingChunk.none(), DocId.NONE, DocId.NONE, true);
        }
    }
}
