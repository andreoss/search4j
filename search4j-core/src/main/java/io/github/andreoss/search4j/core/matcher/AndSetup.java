/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Posting list operators combining the streams of a query tree.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Prepared state of an intersection.
 *
 * @since 0.1.0
 */
@SuppressWarnings({"PMD.DataClass", "PMD.MethodReturnsInternalArray"})
final class AndSetup {

    /**
     * Members in ascending term frequency.
     */
    private final List<PostList> kids;

    /**
     * Highest weight each member can return.
     */
    private final double[] maxima;

    /**
     * Receiver told when the maximum weight changes.
     */
    private final Runnable recalc;

    /**
     * Estimated number of matching documents.
     */
    private final TermCount frequency;

    /**
     * Sum of the member maximum weights.
     */
    private final double total;

    /**
     * Creates prepared state.
     *
     * @param kids Members in ascending term frequency
     * @param maxima Highest weight each member can return
     * @param recalc Receiver told when the maximum weight changes
     * @param frequency Estimated number of matching documents
     * @param total Sum of the member maximum weights
     */
    private AndSetup(
        final List<PostList> kids, final double[] maxima,
        final Runnable recalc, final TermCount frequency, final double total
    ) {
        this.kids = kids;
        this.maxima = maxima;
        this.recalc = recalc;
        this.frequency = frequency;
        this.total = total;
    }

    List<PostList> kids() {
        return this.kids;
    }

    double[] maxima() {
        return this.maxima;
    }

    Runnable recalc() {
        return this.recalc;
    }

    TermCount frequency() {
        return this.frequency;
    }

    double total() {
        return this.total;
    }

    /**
     * Prepares the members of a fresh intersection.
     *
     * @param lists Members
     * @param recalc Receiver told when the maximum weight changes
     * @return Prepared state
     */
    static AndSetup fresh(
        final List<? extends PostList> lists, final Runnable recalc
    ) {
        final List<PostList> kids = new ArrayList<>(lists);
        new Selection<PostList>(
            kids, (left, right) -> left.termFrequency().compareTo(right.termFrequency()) < 0
        ).heapSort();
        return new AndSetup(
            kids, new double[kids.size()],
            Objects.requireNonNull(recalc, "recalculation"),
            AndSetup.estimate(kids), 0.0
        );
    }

    /**
     * Prepares the members of a decayed union.
     *
     * @param decay Members and their maximum weights
     * @param recalc Receiver told when the maximum weight changes
     * @param freq Estimate carried from the decayed union
     * @return Prepared state
     */
    static AndSetup decay(
        final AndDecay decay, final Runnable recalc, final TermCount freq
    ) {
        PostList high = decay.left();
        PostList low = decay.right();
        double hmax = decay.lmax();
        double rlow = decay.rmax();
        if (decay.left().termFrequency().compareTo(
            decay.right().termFrequency()
        ) < 0) {
            high = decay.right();
            low = decay.left();
            hmax = decay.rmax();
            rlow = decay.lmax();
        }
        final List<PostList> kids = new ArrayList<>(2);
        kids.add(low);
        kids.add(high);
        return new AndSetup(
            kids, new double[] {rlow, hmax},
            Objects.requireNonNull(recalc, "recalculation"),
            freq, hmax + rlow
        );
    }

    private static TermCount estimate(final List<PostList> kids) {
        TermCount result = TermCount.ZERO;
        if (!kids.isEmpty()) {
            DocRange range = kids.get(0).docIdRange(PostListMath.WHOLE);
            for (int idx = 1; idx < kids.size(); ++idx) {
                range = PostListMath.narrow(
                    range, kids.get(idx).docIdRange(range)
                );
            }
            if (range.last().compareTo(range.first()) >= 0) {
                double value = PostListMath.length(range);
                for (final PostList kid : kids) {
                    final DocRange sub = kid.docIdRange(PostListMath.WHOLE);
                    value *= (double) kid.termFrequency().value()
                        / PostListMath.length(sub);
                }
                result = PostListMath.rounded(value);
            }
        }
        return result;
    }
}
