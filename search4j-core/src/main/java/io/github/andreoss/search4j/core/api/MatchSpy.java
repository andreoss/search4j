/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * A match spy: observes the matching process.
 *
 * <p>A match spy is notified for each document that contributes to the
 * match set, before the MSet is finalised. It can be used for
 * facets, statistics, or custom post-processing.</p>
 *
 * @since 0.1.0
 */
public interface MatchSpy {

    /**
     * Notified when a document is added to the match set.
     *
     * @param docid Document identifier
     * @param weight Weight of the document
     * @param wdf Within-document frequency of the matching terms
     */
    void add(DocId docid, double weight, TermCount wdf);

    /**
     * Returns the final result of the spy.
     *
     * @return Spy-specific result
     */
    Object result();
}
