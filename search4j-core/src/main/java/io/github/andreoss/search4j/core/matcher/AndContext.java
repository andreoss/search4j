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
import java.util.function.Supplier;

/**
 * Subqueries gathered under a conjunction, with the documents excluded and
 * the optional subqueries adding weight.
 *
 * @since 0.3.0
 */
final class AndContext {

    /**
     * Gathered posting lists.
     */
    private final List<PostList> lists;

    /**
     * Estimates of the gathered posting lists.
     */
    private final List<MatchEstimate> estimates;

    /**
     * Subqueries whose documents are excluded.
     */
    private final UnionContext excluded;

    /**
     * Subqueries adding weight to the documents matched.
     */
    private final UnionContext optional;

    /**
     * Lowest document of the intersection.
     */
    private int first;

    /**
     * Highest document of the intersection.
     */
    private int last;

    /**
     * Whether a weightless match-all subquery was seen.
     */
    private boolean everything;

    AndContext() {
        this.lists = new ArrayList<>(4);
        this.estimates = new ArrayList<>(4);
        this.excluded = new UnionContext();
        this.optional = new UnionContext();
        this.first = 1;
        this.last = Integer.MAX_VALUE;
    }

    boolean add(final MatchTree tree) {
        boolean result = false;
        if (tree != null) {
            this.lists.add(tree.list());
            this.estimates.add(tree.estimate());
            final DocRange range = tree.list().docIdRange(
                new DocRange(new DocId(this.first), new DocId(this.last))
            );
            this.first = Math.max(this.first, range.first().value());
            this.last = Math.min(this.last, range.last().value());
            result = this.first <= this.last;
        }
        if (!result) {
            this.lists.clear();
            this.estimates.clear();
            this.everything = false;
        }
        return result;
    }

    void everything() {
        this.everything = true;
    }

    UnionContext excluded() {
        return this.excluded;
    }

    UnionContext optional() {
        return this.optional;
    }

    MatchTree conjunction(
        final Supplier<MatchTree> all, final TermCount dbsize,
        final Runnable recalc
    ) {
        MatchTree result = null;
        if (this.lists.isEmpty()) {
            if (this.everything) {
                result = all.get();
            }
        } else if (this.lists.size() == 1) {
            result = new MatchTree(this.lists.get(0), this.estimates.get(0));
        } else {
            result = new MatchTree(
                new AndPostList(this.lists, recalc),
                MatchEstimate.of(EstimateKind.AND, this.range(), this.estimates)
            );
        }
        if (result != null && !this.excluded.empty()
            && this.excluded.overlaps(this.first, this.last)) {
            final MatchTree right = this.excluded.disjunction(true, dbsize, recalc);
            result = new MatchTree(
                new AndNotPostList(result.list(), right.list(), dbsize),
                MatchEstimate.of(
                    EstimateKind.AND_NOT, this.range(),
                    List.of(result.estimate(), right.estimate())
                )
            );
        }
        if (result != null && !this.optional.empty()
            && this.optional.overlaps(this.first, this.last)) {
            result = new MatchTree(
                new AndMaybePostList(
                    result.list(),
                    this.optional.disjunction(false, dbsize, recalc).list(), recalc
                ),
                result.estimate()
            );
        }
        return result;
    }

    private DocRange range() {
        return new DocRange(new DocId(this.first), new DocId(this.last));
    }
}
