/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.backend.ShardStatistics;
import io.github.andreoss.search4j.core.backend.list.DocStats;

/**
 * A weighting scheme: computes term weight contributions for scoring.
 *
 * <p>A Weight instance is created per query by the scheme's factory
 * method; it receives the query tree and database statistics and can
 * produce per-term weights for any term in the query.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public interface Weight {

    /**
     * Initialises the weight for a query.
     *
     * @param database Database statistics
     * @param query The query tree
     * @param terms Number of distinct terms in the query
     */
    void init(ShardStatistics database, Query query, int terms);

    /**
     * Weight contribution of a term for a document.
     *
     * @param term Term text
     * @param stats Within-query and within-document frequencies
     * @param doc Length statistics of the document
     * @return Weight contribution, non-negative
     */
    double weight(String term, TermStats stats, DocStats doc);

    /**
     * Creates an uninitialised copy for a parallel or nested query.
     *
     * @return Fresh weight instance
     */
    Weight copy();

    /**
     * Serialises the weight to the reference wire format.
     *
     * @return Bytes of the weight
     */
    byte[] serialise();

    /**
     * Unserialises a weight written in the reference wire format.
     *
     * @param data Bytes of exactly one weight
     * @return The weight
     */
    static Weight deserialise(final byte[] data) {
        return WeightInternal.deserialise(data);
    }
}
