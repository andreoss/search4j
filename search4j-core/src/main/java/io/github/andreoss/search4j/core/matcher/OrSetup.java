/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Prepared state of a weighted union.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.List;
import java.util.Objects;

/**
 * Union members and their matching estimates before iteration starts.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.DataClass")
final class OrSetup {

    /**
     * Left member.
     */
    private final PostList left;

    /**
     * Right member.
     */
    private final PostList right;

    /**
     * Receiver told when the maximum weight changes.
     */
    private final Runnable recalc;

    /**
     * Estimated number of matching documents.
     */
    private final TermCount frequency;

    /**
     * Estimate tree associated with the union.
     */
    private final MatchEstimate estimate;

    /**
     * Creates prepared state.
     *
     * @param left Left member
     * @param right Right member
     * @param recalc Receiver told when the maximum weight changes
     * @param frequency Estimated number of matching documents
     * @param estimate Query estimate node
     */
    private OrSetup(
        final PostList left, final PostList right, final Runnable recalc,
        final TermCount frequency, final MatchEstimate estimate
    ) {
        this.left = left;
        this.right = right;
        this.recalc = recalc;
        this.frequency = frequency;
        this.estimate = estimate;
    }

    PostList left() {
        return this.left;
    }

    PostList right() {
        return this.right;
    }

    Runnable recalc() {
        return this.recalc;
    }

    TermCount frequency() {
        return this.frequency;
    }

    MatchEstimate estimate() {
        return this.estimate;
    }

    /**
     * Prepares the members and estimates of a weighted union.
     *
     * @param left Left member
     * @param right Right member
     * @param recalculation Receiver told when the maximum weight changes
     * @return Prepared union state
     */
    static OrSetup fresh(
        final PostList left, final PostList right,
        final Runnable recalculation
    ) {
        final PostList lft = Objects.requireNonNull(left, "left");
        final PostList rgt = Objects.requireNonNull(right, "right");
        return new OrSetup(
            lft, rgt, Objects.requireNonNull(recalculation, "recalculation"),
            OrPostList.estimate(lft, rgt),
            MatchEstimate.union(List.of(lft, rgt))
        );
    }
}
