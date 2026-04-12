/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Statistics of the query term a weight is prepared for.
 *
 * @param freq Number of documents indexed by the term
 * @param relFreq Number of relevant documents indexed by the term
 * @param collFreq Sum of the within-document frequencies of the term
 * @param wqf Within-query frequency
 * @param wdfMax Upper bound on the within-document frequency
 * @since 0.1.0
 */
public record TermStats(
    TermCount freq, TermCount relFreq, TermCount collFreq, TermCount wqf,
    TermCount wdfMax
) {

    /**
     * Statistics a term-independent weight is prepared with.
     */
    public static final TermStats EXTRA = new TermStats(
        TermCount.ZERO, TermCount.ZERO, TermCount.ZERO, TermCount.ONE,
        TermCount.ZERO
    );
}
