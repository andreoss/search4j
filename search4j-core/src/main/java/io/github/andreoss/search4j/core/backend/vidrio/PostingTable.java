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
import io.github.andreoss.search4j.core.error.DocNotFoundError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.Optional;

/**
 * Lookups in a posting list table: term statistics, document lengths and
 * the range of documents in use.
 *
 * <p>Lookups share one walk over the document lengths and are not
 * thread-safe.</p>
 *
 * @since 0.1.0
 */
public final class PostingTable implements TermFrequencySource {

    /**
     * The table.
     */
    private final TableLookup table;

    /**
     * Walk over the document lengths.
     */
    private final ChunkWalker lengths;

    /**
     * Creates the lookups from their parts.
     *
     * @param table Table looked up
     * @param lengths Walk over the document lengths
     */
    private PostingTable(final TableLookup table, final ChunkWalker lengths) {
        this.table = table;
        this.lengths = lengths;
    }

    /**
     * Opens the lookups over a table.
     *
     * @param table Posting list table
     * @return Lookups over the table
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static PostingTable of(final TableLookup table) {
        return new PostingTable(
            table, ChunkWalker.open(table.cursor(), new byte[0])
        );
    }

    /**
     * Statistics of a term.
     *
     * @param term Term text
     * @return Frequencies and the bound on the within-document frequency:
     *  the collection frequency when it is 0 or the term is in one
     *  document, otherwise the larger of the first document's frequency
     *  and the rest
     */
    public TermStats stats(final String term) {
        final Optional<byte[]> tag = this.table.get(
            PostListKeys.first(PostListKeys.bytes(term))
        );
        TermStats result = TermStats.NONE;
        if (tag.isPresent()) {
            final ByteReader reader = ByteReader.over(tag.get());
            final FirstHeader head = FirstHeader.read(reader);
            final int total = head.freqs().collection().value();
            TermCount bound = head.freqs().collection();
            if (total != 0 && head.freqs().termFrequency().value() != 1) {
                ChunkHeader.read(reader, head.first());
                final int first = PostingFields.count(
                    reader, "Within-document frequency"
                ).value();
                bound = new TermCount(Math.max(total - first, first));
            }
            result = new TermStats(head.freqs(), bound);
        }
        return result;
    }

    @Override
    public TermFrequencies frequencies(final String term) {
        return this.stats(term).freqs();
    }

    /**
     * Tests whether a term has a posting list.
     *
     * @param term Term text
     * @return True when the first chunk exists
     */
    public boolean hasTerm(final String term) {
        return this.table.contains(
            PostListKeys.first(PostListKeys.bytes(term))
        );
    }

    /**
     * Length of a document.
     *
     * @param doc Document identifier
     * @return Sum of the frequencies of its terms
     */
    public TermCount docLength(final DocId doc) {
        if (!this.lengths.jumpTo(doc)) {
            throw new DocNotFoundError(
                String.format("Document %s not found", doc)
            );
        }
        return this.lengths.wdf();
    }

    /**
     * Tests whether a document exists.
     *
     * @param doc Document identifier
     * @return True when it has a length
     */
    public boolean hasDocument(final DocId doc) {
        return this.lengths.jumpTo(doc);
    }

    /**
     * Range of the documents in use.
     *
     * @return Lowest and highest document, both none for an empty table
     */
    public DocRange docIdRange() {
        final TableCursor cursor = this.table.cursor();
        final byte[] term = new byte[0];
        DocRange result = new DocRange(DocId.NONE, DocId.NONE);
        if (cursor.exact(PostListKeys.first(term))) {
            final DocId first =
                FirstHeader.read(ByteReader.over(cursor.tag())).first();
            cursor.seek(PostListKeys.last(term));
            final ChunkKey key = ChunkKey.of(cursor.key());
            if (!key.matches(term)) {
                throw new DatabaseCorruptError(
                    "Last document length chunk not found"
                );
            }
            final ByteReader reader = ByteReader.over(cursor.tag());
            final DocId start;
            if (key.first()) {
                start = FirstHeader.read(reader).first();
            } else {
                start = key.start();
            }
            result = new DocRange(first, ChunkHeader.read(reader, start).end());
        }
        return result;
    }
}
