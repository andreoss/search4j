/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.api.query.Query;
import java.io.IOException;
import java.util.List;

/**
 * Search and expansion rows of a database over counts at and above two to
 * the thirty-one, in the layout of the reference driver.
 *
 * @since 0.8.0
 */
final class CountSearches {

    /**
     * Expansion schemes replayed: the default, Bo1 and the probabilistic
     * scheme with two parameters.
     */
    private static final List<String> EXPANSIONS =
        List.of("-", "bo1", "prob:0.5", "prob:2");

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
    CountSearches(final List<String[]> rows, final String backend) {
        this.rows = rows;
        this.backend = backend;
    }

    /**
     * Adds the matches and counts of every query of a corpus under every
     * weighting scheme.
     *
     * @param corpus Corpus name
     * @param base Database holding the corpus
     * @throws IOException When the queries or schemes cannot be read
     */
    void search(final String corpus, final Database base) throws IOException {
        final List<String[]> queries = WeightVectors.rows("count-queries.tsv");
        for (final String[] spec : WeightVectors.rows("weight-specs.txt")) {
            for (final String[] query : queries) {
                if (query[1].equals(corpus)) {
                    this.match(base, spec[0], query);
                }
            }
        }
    }

    /**
     * Adds the expansion sets of the matches of term a under every
     * expansion scheme.
     *
     * @param corpus Corpus name
     * @param base Database holding the corpus
     */
    void expand(final String corpus, final Database base) {
        for (final String scheme : CountSearches.EXPANSIONS) {
            final Enquire enquire = base.enquire();
            enquire.setQuery(Query.term("a"));
            final int relevant = enquire.getMSet(0, 100).toList().size();
            final List<ESet.Item> items = CountSearches.expansion(enquire, scheme)
                .toList();
            for (int rank = 0; rank < items.size(); ++rank) {
                this.rows.add(
                    CountRows.row(
                        "X", this.backend, corpus, scheme, rank,
                        items.get(rank).term(), items.get(rank).weight()
                    )
                );
            }
            this.rows.add(
                CountRows.row(
                    "Z", this.backend, corpus, scheme, items.size(), relevant
                )
            );
        }
    }

    private void match(final Database base, final String spec, final String... query) {
        final Enquire enquire = base.enquire();
        enquire.setQuery(CountVectors.build(query[4], query[5]));
        enquire.setWeightingScheme(WeightVectors.parse(spec));
        final MSet found = enquire.getMSet(0, 1000);
        final List<MSet.Item> items = found.toList();
        for (int rank = 0; rank < items.size(); ++rank) {
            this.rows.add(
                CountRows.row(
                    "D", this.backend, spec, query[0], rank,
                    items.get(rank).docId(), items.get(rank).weight()
                )
            );
        }
        this.rows.add(
            CountRows.row(
                "C", this.backend, spec, query[0], items.size(),
                found.matchesLowerBound(), found.matchesEstimated(),
                found.matchesUpperBound()
            )
        );
    }

    private static ESet expansion(final Enquire enquire, final String scheme) {
        final ESet found;
        if ("-".equals(scheme)) {
            found = enquire.getESet(20);
        } else if ("bo1".equals(scheme)) {
            found = enquire.getESet(20, new Bo1EWeight());
        } else {
            found = enquire.getESet(
                20, new ProbeWeight(Double.parseDouble(scheme.substring(5)))
            );
        }
        return found;
    }
}
