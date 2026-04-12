/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Matcher core of the search engine.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.api.CollectionStats;
import io.github.andreoss.search4j.core.api.LengthBounds;
import io.github.andreoss.search4j.core.api.TermStats;
import io.github.andreoss.search4j.core.api.Weight;
import io.github.andreoss.search4j.core.api.WeightStats;
import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.backend.Shard;
import io.github.andreoss.search4j.core.backend.TermFrequencies;
import io.github.andreoss.search4j.core.backend.list.LeafPostList;
import io.github.andreoss.search4j.core.backend.list.TermWeight;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Weighting scheme of a search with the collection statistics it is
 * prepared with.
 *
 * @param weight Weighting scheme
 * @param collection Collection and query statistics
 * @since 0.1.0
 */
record Scheme(Weight weight, CollectionStats collection) {

    /**
     * Gathers the statistics of a search.
     *
     * @param weight Weighting scheme
     * @param shard Shard searched
     * @param query Query run
     * @return The scheme with its statistics
     */
    static Scheme of(final Weight weight, final Shard shard, final Query query) {
        return new Scheme(
            weight,
            new CollectionStats(
                shard.docCount(), TermCount.ZERO, shard.totalLength(),
                query.length(),
                new LengthBounds(
                    shard.docLengthLowerBound(), shard.docLengthUpperBound(),
                    shard.uniqueTermsLowerBound()
                )
            )
        );
    }

    static TermStats stats(
        final LeafPostList leaf, final TermFrequencies freqs, final TermCount wqf
    ) {
        return new TermStats(
            freqs.termFrequency(), TermCount.ZERO, freqs.collection(),
            wqf, leaf.wdfUpperBound()
        );
    }

    /**
     * Prepares the weight of a term.
     *
     * @param stats Statistics of the term
     * @param factor Scale factor of the term
     * @return Term weight
     */
    TermWeight term(final TermStats stats, final double factor) {
        return this.weight.term(new WeightStats(this.collection, stats), factor);
    }

    /**
     * Prepares the term-independent weight.
     *
     * @return Extra weight
     */
    ExtraWeight extra() {
        return this.weight.extra(new WeightStats(this.collection, TermStats.EXTRA));
    }
}
