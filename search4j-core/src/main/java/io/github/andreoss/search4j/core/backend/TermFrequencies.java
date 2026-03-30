/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Frequencies of one term in a shard.
 *
 * @param termFrequency Number of documents indexed by the term
 * @param collection Sum of the within-document frequencies
 * @since 0.1.0
 */
public record TermFrequencies(
    TermCount termFrequency,
    TermCount collection
) {

    /**
     * Frequencies of a term indexing nothing.
     */
    public static final TermFrequencies NONE =
        new TermFrequencies(TermCount.ZERO, TermCount.ZERO);
}
