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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Prepared state of an exclusive union.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.MethodReturnsInternalArray")
final class XorSetup {

    /**
     * Members.
     */
    private final PostList[] lists;

    /**
     * Number of members.
     */
    private final int size;

    /**
     * Estimated number of matching documents.
     */
    private final TermCount frequency;

    /**
     * Receiver told when the maximum weight changes.
     */
    private final Runnable recalc;

    /**
     * Estimate tree associated with the exclusive union.
     */
    private final MatchEstimate matching;

    /**
     * Creates prepared state.
     *
     * @param lists Members
     * @param size Number of members
     * @param frequency Estimated number of matching documents
     * @param recalc Receiver told when the maximum weight changes
     * @param estimate Query estimate node
     */
    private XorSetup(
        final PostList[] lists, final int size,
        final TermCount frequency, final Runnable recalc,
        final MatchEstimate estimate
    ) {
        this.lists = lists;
        this.size = size;
        this.frequency = frequency;
        this.recalc = recalc;
        this.matching = estimate;
    }

    PostList[] lists() {
        return this.lists;
    }

    int size() {
        return this.size;
    }

    TermCount frequency() {
        return this.frequency;
    }

    Runnable recalc() {
        return this.recalc;
    }

    MatchEstimate estimate() {
        return this.matching;
    }

    /**
     * Prepares the members and estimate of an exclusive union.
     *
     * @param members Members
     * @param dbsize Number of documents in the database
     * @param recalc Receiver told when the maximum weight changes
     * @return Prepared state
     */
    static XorSetup fresh(
        final List<? extends PostList> members, final TermCount dbsize,
        final Runnable recalc
    ) {
        if (dbsize.value() <= 0) {
            throw new InvalidArgumentError("Database size must be positive");
        }
        final PostList[] lists = members.toArray(PostList[]::new);
        return new XorSetup(
            lists, lists.length,
            XorSetup.estimate(lists, lists.length, dbsize),
            Objects.requireNonNull(recalc, "recalculation"),
            MatchEstimate.exclusive(Arrays.asList(lists))
        );
    }

    private static TermCount estimate(
        final PostList[] lists, final int size, final TermCount dbsize
    ) {
        TermCount result = TermCount.ZERO;
        if (size > 0) {
            final double scale = 1.0 / dbsize.value();
            double probability = lists[0].termFrequency().value() * scale;
            for (int index = 1; index < size; ++index) {
                final double current =
                    lists[index].termFrequency().value() * scale;
                probability += current - 2.0 * probability * current;
            }
            result = PostListMath.rounded(probability * dbsize.value());
        }
        return result;
    }
}
