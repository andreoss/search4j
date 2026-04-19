/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TotalLength;

/**
 * Statistics of the collection and query a weight is prepared for.
 *
 * @param size Number of documents
 * @param rset Number of documents marked relevant
 * @param total Sum of the lengths of every document
 * @param query Length of the query, the sum of its within-query frequencies
 * @param bounds Bounds on document lengths
 * @since 0.1.0
 */
public record CollectionStats(
    TermCount size, TermCount rset, TotalLength total, TermCount query,
    LengthBounds bounds
) {

    /**
     * Average length of a document.
     *
     * @return Total length over the number of documents
     */
    public double average() {
        return this.total.asDouble() / this.size.value();
    }
}
