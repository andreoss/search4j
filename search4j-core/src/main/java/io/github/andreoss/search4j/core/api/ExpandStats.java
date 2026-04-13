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
 * Statistics of a term over the relevant documents of an expansion.
 *
 * @param freq Sum of the within-document frequencies of the term in the
 *  relevant documents, each at least one
 * @param dfreq Number of relevant documents indexed by the term
 * @param docs Number of relevant documents
 * @param multiplier Sum over the relevant documents indexed by the term of
 *  the share each contributes, see
 *  {@link ExpandWeight#share(TermCount, TermCount, double)}
 * @since 0.1.0
 */
public record ExpandStats(
    TermCount freq, TermCount dfreq, TermCount docs, double multiplier
) {

    /**
     * Creates statistics where each relevant document indexed by the term
     * contributes a share of one.
     *
     * @param freq Sum of the within-document frequencies of the term in the
     *  relevant documents
     * @param dfreq Number of relevant documents indexed by the term
     * @param docs Number of relevant documents
     */
    public ExpandStats(final TermCount freq, final TermCount dfreq, final TermCount docs) {
        this(freq, dfreq, docs, (double) dfreq.value());
    }
}
