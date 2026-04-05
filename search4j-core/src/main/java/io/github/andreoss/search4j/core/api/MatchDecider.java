/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.DocId;

/**
 * A match decider: filters documents during the search.
 *
 * <p>A match decider is called for each candidate document before it is
 * added to the match set. If it returns false, the document is skipped
 * and the search continues.</p>
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface MatchDecider {

    /**
     * Tests whether a document should be included in the results.
     *
     * @param docid Candidate document identifier
     * @return True to include, false to exclude
     */
    boolean decide(DocId docid);
}
