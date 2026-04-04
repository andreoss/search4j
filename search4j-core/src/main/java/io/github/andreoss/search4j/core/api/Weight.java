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
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * A weighting scheme: computes term weight contributions for scoring.
 *
 * <p>A Weight instance is created per query by the scheme's factory
 * method; it receives the query tree and database statistics and can
 * produce per-term weights for any term in the query.</p>
 *
 * @since 0.1.0
 */
public interface Weight {

    /**
     * Initialises the weight for a query.
     *
     * @param database Database statistics
     * @param query The query tree
     * @param termCount Number of distinct terms in the query
     */
    void init(ShardStatistics database, Query query, int termCount);

    /**
     * Weight contribution of a term for a document.
     *
     * @param term Term text
     * @param wqf Within-query frequency
     * @param wdf Within-document frequency
     * @param docLength Length of the document
     * @param uniqueTerms Distinct term count of the document
     * @return Weight contribution, non-negative
     */
    double weight(
        String term, TermCount wqf, TermCount wdf, TermCount docLength,
        TermCount uniqueTerms
    );

    /**
     * Weight contribution of a term for a document with position data.
     *
     * @param term Term text
     * @param wqf Within-query frequency
     * @param wdf Within-document frequency
     * @param docLength Length of the document
     * @param uniqueTerms Distinct term count of the document
     * @param positions Positions of the term in the document
     * @return Weight contribution, non-negative
     */
    double weight(
        String term, TermCount wqf, TermCount wdf, TermCount docLength,
        TermCount uniqueTerms, java.util.List<TermCount> positions
    );

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
    static Weight deserialise(byte[] data) {
        return WeightInternal.deserialise(data);
    }
}
