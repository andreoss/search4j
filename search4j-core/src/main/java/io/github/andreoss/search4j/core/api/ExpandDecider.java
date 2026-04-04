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
 * A decider for query expansion: filters terms during ESet generation.
 *
 * <p>An ExpandDecider is called for each candidate term during expansion.
 * If it returns false, the term is excluded from the expansion set.</p>
 *
 * @since 0.1.0
 */
public interface ExpandDecider {

    /**
     * Tests whether a term should be included in the expansion set.
     *
     * @param term Candidate term
     * @param termFreq Estimated term frequency in the database
     * @return True to include, false to exclude
     */
    boolean decide(String term, TermCount termFreq);
}
