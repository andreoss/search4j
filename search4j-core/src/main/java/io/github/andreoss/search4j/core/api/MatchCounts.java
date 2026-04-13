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
 * Bounds and estimate of the number of documents a search matches, after
 * and before collapsing.
 *
 * @since 0.7.0
 */
public interface MatchCounts {

    /**
     * A lower bound on the number of matching documents.
     *
     * @return Lower bound, zero when empty
     */
    TermCount matchesLowerBound();

    /**
     * An estimate of the number of matching documents.
     *
     * @return Estimate between the bounds, rounded to the precision the
     *  bounds justify once it and their distance both exceed ten, zero when
     *  empty
     */
    TermCount matchesEstimated();

    /**
     * An upper bound on the number of matching documents.
     *
     * @return Upper bound, zero when empty
     */
    TermCount matchesUpperBound();

    /**
     * A lower bound on the number of matching documents had no matches been
     * collapsed.
     *
     * @return Lower bound, the same as {@link #matchesLowerBound()} when not
     *  collapsing
     * @since 0.7.0
     */
    TermCount uncollapsedMatchesLowerBound();

    /**
     * An estimate of the number of matching documents had no matches been
     * collapsed.
     *
     * @return Estimate between the uncollapsed bounds, rounded as
     *  {@link #matchesEstimated()} is, the same as
     *  {@link #matchesEstimated()} when not collapsing
     * @since 0.7.0
     */
    TermCount uncollapsedMatchesEstimated();

    /**
     * An upper bound on the number of matching documents had no matches been
     * collapsed.
     *
     * @return Upper bound, the same as {@link #matchesUpperBound()} when not
     *  collapsing
     * @since 0.7.0
     */
    TermCount uncollapsedMatchesUpperBound();
}
