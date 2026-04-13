/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.DocId;
import java.io.IOException;
import java.util.List;

/**
 * Rows a database gives over counts at and above two to the thirty-one, in
 * the layout of the reference driver: statistics, term frequencies,
 * postings, document lengths, term lists, searches with every weighting
 * scheme and expansions.
 *
 * @since 0.8.0
 */
final class CountRows {

    /**
     * Rows produced.
     */
    private final List<String[]> rows;

    /**
     * Backend name of every row.
     */
    private final String backend;

    /**
     * Creates a producer.
     *
     * @param rows Rows produced
     * @param backend Backend name of every row
     */
    CountRows(final List<String[]> rows, final String backend) {
        this.rows = rows;
        this.backend = backend;
    }

    /**
     * Adds every row of one database.
     *
     * @param corpus Corpus name
     * @param base Database holding the corpus
     * @throws IOException When the queries cannot be read
     */
    void add(final String corpus, final Database base) throws IOException {
        this.statistics(corpus, base);
        final CountSearches searches = new CountSearches(this.rows, this.backend);
        searches.search(corpus, base);
        searches.expand(corpus, base);
    }

    /**
     * Joins fields into a row.
     *
     * @param fields Fields, written as text
     * @return Row
     */
    static String[] row(final Object... fields) {
        final String[] result = new String[fields.length];
        for (int idx = 0; idx < fields.length; ++idx) {
            result[idx] = String.valueOf(fields[idx]);
        }
        return result;
    }

    private void statistics(final String corpus, final Database base) {
        this.rows.add(
            CountRows.row(
                "G", this.backend, corpus, base.docCount(), base.lastDocId(),
                base.totalLength(), base.averageLength().value(),
                base.docLengthLowerBound(), base.docLengthUpperBound()
            )
        );
        try (TermIterator terms = base.allTerms("")) {
            while (terms.hasNext()) {
                final String term = terms.next();
                this.rows.add(
                    CountRows.row(
                        "T", this.backend, corpus, term, base.termFrequency(term),
                        base.collectionFrequency(term), base.wdfUpperBound(term)
                    )
                );
                this.postings(corpus, base, term);
            }
        }
        try (PostingIterator docs = base.postings("")) {
            while (docs.hasNext()) {
                final DocId doc = docs.next();
                this.rows.add(
                    CountRows.row(
                        "L", this.backend, corpus, doc, base.docLength(doc),
                        base.uniqueTerms(doc)
                    )
                );
                this.terms(corpus, base, doc);
            }
        }
    }

    private void postings(final String corpus, final Database base, final String term) {
        try (PostingIterator postings = base.postings(term)) {
            while (postings.hasNext()) {
                final DocId doc = postings.next();
                this.rows.add(
                    CountRows.row(
                        "P", this.backend, corpus, term, doc, postings.wdf(),
                        postings.docLength()
                    )
                );
            }
        }
    }

    private void terms(final String corpus, final Database base, final DocId doc) {
        try (TermIterator list = base.termList(doc)) {
            while (list.hasNext()) {
                final String term = list.next();
                this.rows.add(
                    CountRows.row("W", this.backend, corpus, doc, term, list.wdf())
                );
            }
        }
    }
}
