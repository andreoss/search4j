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
 * An expansion weight scheme: computes term weights for query expansion.
 *
 * <p>An ExpandWeight instance computes the weight of a term in the
 * expansion set based on its statistics in the matching documents
 * and the database as a whole.</p>
 *
 * @since 0.1.0
 */
public interface ExpandWeight {

    /**
     * Initialises the expansion weight for a query.
     *
     * @param database Database statistics
     * @param query The query tree
     * @param termCount Number of distinct terms in the query
     */
    void init(ShardStatistics database, Query query, int termCount);

    /**
     * Weight of a term for expansion.
     *
     * @param term Term text
     * @param wqf Within-query frequency (always 1 for expansion terms)
     * @param termFreq Estimated term frequency in the database
     * @param docFreq Document frequency in the matching set
     * @param matchingDocs Number of matching documents
     * @return Expansion weight, non-negative
     */
    double weight(
        String term, TermCount wqf, TermCount termFreq,
        TermCount docFreq, TermCount matchingDocs
    );

    /**
     * Creates an uninitialised copy for a parallel or nested query.
     *
     * @return Fresh expansion weight instance
     */
    ExpandWeight copy();

    /**
     * Serialises the expansion weight to the reference wire format.
     *
     * @return Bytes of the weight
     */
    byte[] serialise();

    /**
     * Unserialises an expansion weight written in the reference wire format.
     *
     * @param data Bytes of exactly one weight
     * @return The weight
     */
    static ExpandWeight deserialise(byte[] data) {
        return ExpandWeightInternal.deserialise(data);
    }
}
