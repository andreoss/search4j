/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.backend.Shard;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Query expansion: terms of the relevant documents other than those of the
 * query, weighted by an expansion scheme.
 *
 * @since 0.4.0
 */
final class Expansion {

    /**
     * Database the documents belong to.
     */
    private final Shard shard;

    /**
     * Query whose terms are left out.
     */
    private final Query request;

    /**
     * Relevant documents.
     */
    private final List<DocId> relevant;

    /**
     * Creates an expansion.
     *
     * @param shard Database the documents belong to
     * @param request Query whose terms are left out
     * @param relevant Relevant documents
     */
    Expansion(final Shard shard, final Query request, final List<DocId> relevant) {
        this.shard = shard;
        this.request = request;
        this.relevant = relevant;
    }

    /**
     * Weighted expansion terms, best first.
     *
     * @param max Maximum number of terms, zero for none
     * @param weight Expansion scheme
     * @param chooser Filter of the terms, null for none
     * @return Terms with their weights
     */
    List<ESet.Item> items(
        final int max, final ExpandWeight weight, final ExpandDecider chooser
    ) {
        final ExpandRun run = new ExpandRun(max);
        if (max > 0 && !this.relevant.isEmpty()) {
            final Map<String, double[]> stats = this.collect(weight);
            weight.init(this.shard, this.request, stats.size());
            final Set<String> asked = new HashSet<>(this.request.uniqueTerms());
            final TermCount docs = new TermCount(this.relevant.size());
            for (final Map.Entry<String, double[]> entry : stats.entrySet()) {
                final String term = entry.getKey();
                final double[] values = entry.getValue();
                final TermCount freq = new TermCount((int) values[0]);
                if (!asked.contains(term)
                    && (chooser == null || chooser.decide(term, freq))) {
                    run.offer(
                        term, freq,
                        weight.weight(
                            term,
                            new ExpandStats(
                                freq, new TermCount((int) values[1]), docs, values[2]
                            )
                        )
                    );
                }
            }
        }
        return run.items();
    }

    private Map<String, double[]> collect(final ExpandWeight weight) {
        final Map<String, double[]> stats = new TreeMap<>();
        final double average = this.shard.totalLength().value()
            / (double) this.shard.docCount().value();
        for (final DocId docid : this.relevant) {
            final TermCount length = this.shard.docLength(docid);
            final TermIterator terms = this.shard.openDocument(docid, false)
                .orElseThrow().termList();
            while (terms.hasNext()) {
                final String term = terms.next();
                TermCount wdf = terms.wdf();
                if (wdf.value() == 0) {
                    wdf = TermCount.ONE;
                }
                final double[] values = stats.computeIfAbsent(term, key -> new double[3]);
                values[0] = values[0] + wdf.value();
                values[1] = values[1] + 1.0;
                values[2] = values[2] + weight.share(wdf, length, average);
            }
        }
        return stats;
    }
}
