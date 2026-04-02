/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Posting list operators combining the streams of a query tree.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.Objects;

/**
 * Prepared state of a difference.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.DataClass")
final class AndNotSetup {

    /**
     * Included side.
     */
    private final PostList left;

    /**
     * Excluded side.
     */
    private final PostList right;

    /**
     * Estimated number of matching documents.
     */
    private final TermCount frequency;

    /**
     * Estimate tree associated with the difference.
     */
    private final MatchEstimate estimate;

    /**
     * Creates prepared state.
     *
     * @param left Included side
     * @param right Excluded side
     * @param frequency Estimated number of matching documents
     * @param estimate Query estimate node
     */
    private AndNotSetup(
        final PostList left, final PostList right, final TermCount frequency,
        final MatchEstimate estimate
    ) {
        this.left = left;
        this.right = right;
        this.frequency = frequency;
        this.estimate = estimate;
    }

    PostList left() {
        return this.left;
    }

    PostList right() {
        return this.right;
    }

    TermCount frequency() {
        return this.frequency;
    }

    MatchEstimate estimate() {
        return this.estimate;
    }

    /**
     * Prepares the sides and estimate of a difference.
     *
     * @param left Included side
     * @param right Excluded side
     * @param dbsize Number of documents in the database
     * @return Prepared state
     */
    static AndNotSetup fresh(
        final PostList left, final PostList right, final TermCount dbsize
    ) {
        if (dbsize.value() <= 0) {
            throw new InvalidArgumentError("Database size must be positive");
        }
        final PostList lft = Objects.requireNonNull(left, "left");
        final PostList rgt = Objects.requireNonNull(right, "right");
        return new AndNotSetup(
            lft,
            rgt,
            PostListMath.rounded(
                lft.termFrequency().value()
                    * (double) (dbsize.value() - rgt.termFrequency().value())
                    / dbsize.value()
            ),
            MatchEstimate.difference(lft, rgt)
        );
    }
}
