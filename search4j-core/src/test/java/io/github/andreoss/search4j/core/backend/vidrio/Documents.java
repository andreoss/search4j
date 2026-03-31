/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.TermOrder;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Documents written to a term list table, a posting list table and a
 * position table, with the term lists the model expects.
 *
 * @since 0.1.0
 */
final class Documents {

    /**
     * Posting lists of the documents.
     */
    private final Corpus corpus;

    /**
     * Blocks of the term list table.
     */
    private final MemoryBlocks blocks;

    /**
     * Term list table written.
     */
    private final TableWriter table;

    /**
     * Position table written.
     */
    private final Positions positions;

    /**
     * Term lists committed, by document.
     */
    private TermListTable lists;

    /**
     * Creates the documents from their parts.
     *
     * @param corpus Posting lists of the documents
     * @param blocks Blocks of the term list table
     * @param table Term list table written
     * @param positions Position table written
     */
    private Documents(
        final Corpus corpus, final MemoryBlocks blocks,
        final TableWriter table, final Positions positions
    ) {
        this.corpus = corpus;
        this.blocks = blocks;
        this.table = table;
        this.positions = positions;
    }

    /**
     * Creates an empty set of documents.
     *
     * @param seed Seed of the posting corpus
     * @return Documents
     */
    static Documents of(final long seed) {
        final MemoryBlocks blocks = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        return new Documents(
            Corpus.of(seed, BlockSize.MIN, 1), blocks,
            TableWriter.create(blocks, 18), Positions.of()
        );
    }

    /**
     * Adds a document to both tables.
     *
     * @param doc Document number, not present
     * @param terms Its terms with their frequency
     */
    void add(final int doc, final Map<String, TermCount> terms) {
        this.corpus.add(DocId.of(doc), terms);
        new TermListWriter(this.table).set(DocId.of(doc), terms);
    }

    /**
     * Sets the positions of a term in a document.
     *
     * @param doc Document number
     * @param term Term
     * @param list Ascending positions
     */
    void positions(final int doc, final String term, final List<TermPos> list) {
        this.positions.set(doc, term, list);
    }

    /**
     * Commits every table as revision 1.
     */
    void commit() {
        this.lists = TermListTable.of(
            VidrioTable.over(
                this.blocks, this.table.commit(new RevisionNumber(1L)).root()
            ),
            PostingTable.of(this.corpus.table(this.corpus.commit(1L))),
            this.positions.commit()
        );
    }

    /**
     * Documents present.
     *
     * @return Documents in order
     */
    List<DocId> docs() {
        return this.corpus.docs();
    }

    /**
     * Opens the committed term list of a document.
     *
     * @param doc Document present
     * @return Term list before its first term
     */
    VidrioTermList open(final DocId doc) {
        return this.lists.open(doc);
    }

    /**
     * Terms of a document.
     *
     * @param doc Document present
     * @return Its terms with their frequency
     */
    Map<String, TermCount> terms(final DocId doc) {
        return this.corpus.terms(doc);
    }

    /**
     * Length of a document.
     *
     * @param doc Document present
     * @return Sum of the frequencies of its terms
     */
    TermCount length(final DocId doc) {
        TermCount total = TermCount.ZERO;
        for (final TermCount wdf : this.terms(doc).values()) {
            total = total.plus(wdf);
        }
        return total;
    }

    /**
     * Term list of a document as the model expects it.
     *
     * @param doc Document present
     * @return Term, frequency and term frequency triples in term order
     */
    List<String> expected(final DocId doc) {
        final Map<String, TermCount> terms = new TreeMap<>(TermOrder.INSTANCE);
        terms.putAll(this.terms(doc));
        final List<String> result = new ArrayList<>(terms.size());
        for (final Map.Entry<String, TermCount> term : terms.entrySet()) {
            result.add(
                Documents.entry(
                    term.getKey(), term.getValue(),
                    new TermCount(this.corpus.expected(term.getKey()).size())
                )
            );
        }
        return result;
    }

    /**
     * Term list read to its end.
     *
     * @param list List before its first term
     * @return Term, frequency and term frequency triples in list order
     */
    static List<String> read(final VidrioTermList list) {
        final List<String> result = new ArrayList<>(0);
        list.next();
        while (!list.atEnd()) {
            result.add(
                Documents.entry(list.term(), list.wdf(), list.termFrequency())
            );
            list.next();
        }
        return result;
    }

    private static String entry(
        final String term, final TermCount wdf, final TermCount freq
    ) {
        return String.format("%s:%s:%s", term, wdf, freq);
    }
}
