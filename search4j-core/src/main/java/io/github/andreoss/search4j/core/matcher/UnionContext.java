/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Matcher core of the search engine.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.ArrayList;
import java.util.List;

/**
 * Subqueries gathered under a disjunction or an exclusive disjunction, with
 * the union of their document ranges.
 *
 * @since 0.3.0
 */
final class UnionContext {

    /**
     * Gathered posting lists.
     */
    private final List<PostList> lists;

    /**
     * Estimates of the gathered posting lists.
     */
    private final List<MatchEstimate> estimates;

    /**
     * Lowest document of the union.
     */
    private int first;

    /**
     * Highest document of the union.
     */
    private int last;

    UnionContext() {
        this.lists = new ArrayList<>(4);
        this.estimates = new ArrayList<>(4);
        this.first = Integer.MAX_VALUE;
        this.last = 0;
    }

    void add(final MatchTree tree) {
        if (tree != null) {
            this.lists.add(tree.list());
            this.estimates.add(tree.estimate());
            final DocRange range = tree.list().docIdRange(PostListMath.WHOLE);
            this.first = Math.min(this.first, range.first().value());
            this.last = Math.max(this.last, range.last().value());
        }
    }

    boolean empty() {
        return this.lists.isEmpty();
    }

    boolean overlaps(final int lower, final int upper) {
        return this.last >= lower && this.first <= upper;
    }

    MatchTree disjunction(
        final boolean plain, final TermCount dbsize, final Runnable recalc
    ) {
        final MatchTree result;
        if (this.lists.isEmpty()) {
            result = null;
        } else if (this.lists.size() == 1) {
            result = new MatchTree(this.lists.get(0), this.estimates.get(0));
        } else if (plain) {
            result = new MatchTree(
                new BoolOrPostList(this.lists, dbsize), this.estimate(EstimateKind.OR)
            );
        } else {
            result = new MatchTree(
                new FrequencyHeap(this.lists).tree(recalc),
                this.estimate(EstimateKind.OR)
            );
        }
        return result;
    }

    MatchTree exclusive(final TermCount dbsize, final Runnable recalc) {
        final MatchTree result;
        if (this.lists.isEmpty()) {
            result = null;
        } else if (this.lists.size() == 1) {
            result = new MatchTree(this.lists.get(0), this.estimate(EstimateKind.XOR));
        } else {
            result = new MatchTree(
                new XorPostList(this.lists, dbsize, recalc),
                this.estimate(EstimateKind.XOR)
            );
        }
        return result;
    }

    private MatchEstimate estimate(final EstimateKind kind) {
        return MatchEstimate.of(
            kind, new DocRange(new DocId(this.first), new DocId(this.last)),
            this.estimates
        );
    }
}
