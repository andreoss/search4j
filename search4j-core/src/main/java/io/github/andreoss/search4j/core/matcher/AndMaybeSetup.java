/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Posting list operators combining the streams of a query tree.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.Objects;

/**
 * Prepared state of an optional intersection.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.DataClass")
final class AndMaybeSetup {

    /**
     * Required side.
     */
    private final PostList left;

    /**
     * Optional side.
     */
    private final PostList right;

    /**
     * Highest weight the required side can return.
     */
    private final double lmax;

    /**
     * Highest weight the optional side can return.
     */
    private final double rmax;

    /**
     * Receiver told when the maximum weight changes.
     */
    private final Runnable recalc;

    /**
     * Estimated number of matching documents.
     */
    private final TermCount frequency;

    /**
     * Creates prepared state.
     *
     * @param left Required side
     * @param right Optional side
     * @param lmax Highest weight the required side can return
     * @param rmax Highest weight the optional side can return
     * @param recalc Receiver told when the maximum weight changes
     * @param frequency Estimated number of matching documents
     */
    private AndMaybeSetup(
        final PostList left, final PostList right,
        final double lmax, final double rmax,
        final Runnable recalc, final TermCount frequency
    ) {
        this.left = left;
        this.right = right;
        this.lmax = lmax;
        this.rmax = rmax;
        this.recalc = recalc;
        this.frequency = frequency;
    }

    PostList left() {
        return this.left;
    }

    PostList right() {
        return this.right;
    }

    double lmax() {
        return this.lmax;
    }

    double rmax() {
        return this.rmax;
    }

    Runnable recalc() {
        return this.recalc;
    }

    TermCount frequency() {
        return this.frequency;
    }

    /**
     * Prepares the sides and estimate of an optional intersection.
     *
     * @param fresh Children, weights and callback
     * @return Prepared state
     */
    static AndMaybeSetup fresh(final AndMaybeFresh fresh) {
        final PostList lft = Objects.requireNonNull(fresh.left(), "left");
        return new AndMaybeSetup(
            lft, Objects.requireNonNull(fresh.right(), "right"),
            fresh.lmax(), fresh.rmax(),
            Objects.requireNonNull(fresh.recalc(), "recalculation"),
            lft.termFrequency()
        );
    }
}
