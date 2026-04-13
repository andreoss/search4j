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
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public interface ExpandWeight {

    /**
     * Initialises the expansion weight for a query.
     *
     * @param database Database statistics
     * @param query The query tree
     * @param terms Number of distinct terms in the query
     */
    void init(ShardStatistics database, Query query, int terms);

    /**
     * Weight of a term for expansion.
     *
     * @param term Term text
     * @param stats Term and matching statistics
     * @return Expansion weight, terms weighing zero or less left out
     */
    double weight(String term, ExpandStats stats);

    /**
     * Share a relevant document indexed by a term contributes to the
     * multiplier of the term.
     *
     * @param wdf Within-document frequency of the term, at least one
     * @param length Length of the document
     * @param average Average document length of the database
     * @return Share of the document, one unless the scheme weighs it
     */
    default double share(final TermCount wdf, final TermCount length, final double average) {
        return 1.0;
    }

    /**
     * Creates an uninitialised copy for a parallel or nested query.
     *
     * @return Fresh expansion weight instance
     */
    ExpandWeight copy();

    /**
     * Serialises the expansion weight: a type byte, then its parameters.
     *
     * @return Bytes of the weight
     */
    byte[] serialise();

    /**
     * Unserialises an expansion weight written by {@link #serialise()}.
     *
     * @param data Bytes of exactly one weight
     * @return The weight
     */
    static ExpandWeight deserialise(final byte[] data) {
        return ExpandWeightInternal.deserialise(data);
    }
}
