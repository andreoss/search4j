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
import java.util.ArrayList;
import java.util.List;

/**
 * Subqueries gathered under a disjunction, an exclusive disjunction, an
 * elite set or a weighted maximum, with the union of their document ranges
 * and the frequencies of the subqueries under a synonym.
 *
 * <p>Selecting an elite set or the most frequent expansions reorders the
 * posting lists only: the estimates kept are the first ones gathered, as the
 * reference library keeps them.</p>
 *
 * @since 0.3.0
 */
final class UnionContext {

    /**
     * Shard, statistics and callback of the search.
     */
    private final Matching env;

    /**
     * Gathered posting lists.
     */
    private final List<PostList> lists;

    /**
     * Estimates of the gathered posting lists.
     */
    private final List<MatchEstimate> estimates;

    /**
     * Frequencies of the gathered subqueries.
     */
    private final List<TermFreqs> freqs;

    /**
     * Lowest document of the union.
     */
    private int first;

    /**
     * Highest document of the union.
     */
    private int last;

    UnionContext(final Matching env) {
        this.env = env;
        this.lists = new ArrayList<>(4);
        this.estimates = new ArrayList<>(4);
        this.freqs = new ArrayList<>(0);
        this.first = Integer.MAX_VALUE;
        this.last = 0;
    }

    void add(final MatchTree tree, final TermFreqs sink) {
        this.freqs(sink);
        if (tree != null) {
            this.lists.add(tree.list());
            this.estimates.add(tree.estimate());
            final DocRange range = tree.list().docIdRange(PostListMath.WHOLE);
            this.first = Math.min(this.first, range.first().value());
            this.last = Math.max(this.last, range.last().value());
        }
    }

    void freqs(final TermFreqs sink) {
        if (sink != null) {
            this.freqs.add(sink.copy());
        }
    }

    boolean empty() {
        return this.lists.isEmpty();
    }

    int size() {
        return this.lists.size();
    }

    List<PostList> lists() {
        return this.lists;
    }

    boolean overlaps(final int lower, final int upper) {
        return this.last >= lower && this.first <= upper;
    }

    void elite(final int keep, final int among) {
        final int from = this.lists.size() - among;
        new Selection<PostList>(
            this.lists, (left, right) -> left.recalcMaxWeight() > right.recalcMaxWeight()
        ).nth(from, from + keep - 1, this.lists.size());
        this.shrink(from + keep);
    }

    void frequent(final long keep) {
        if (keep == 0L) {
            this.shrink(0);
        } else if (this.lists.size() > keep) {
            new Selection<PostList>(
                this.lists,
                (left, right) -> left.termFrequency().compareTo(right.termFrequency()) > 0
            ).nth(0, (int) keep - 1, this.lists.size());
            this.shrink((int) keep);
        }
    }

    MatchTree disjunction(final TermFreqs sink, final boolean plain) {
        if (!this.freqs.isEmpty()) {
            this.estimateOr(sink);
        }
        final MatchTree result;
        if (this.lists.isEmpty()) {
            result = null;
        } else if (this.lists.size() == 1) {
            result = new MatchTree(this.lists.get(0), this.estimates.get(0));
        } else if (plain) {
            result = new MatchTree(
                new BoolOrPostList(this.lists, this.env.dbsize()),
                this.estimate(EstimateKind.OR)
            );
        } else {
            result = new MatchTree(
                new FrequencyHeap(this.lists).tree(this.env.recalc()),
                this.estimate(EstimateKind.OR)
            );
        }
        return result;
    }

    MatchTree maximum() {
        final MatchTree result;
        if (this.lists.isEmpty()) {
            result = null;
        } else if (this.lists.size() == 1) {
            result = new MatchTree(this.lists.get(0), this.estimates.get(0));
        } else {
            final List<PostList> sorted = new ArrayList<>(this.lists);
            new Selection<PostList>(
                sorted,
                (left, right) -> left.termFrequency().compareTo(right.termFrequency()) > 0
            ).sort();
            result = new MatchTree(
                new MaxPostList(sorted, this.env.recalc(), this.env.dbsize()),
                this.estimate(EstimateKind.OR)
            );
        }
        return result;
    }

    MatchTree exclusive(final TermFreqs sink) {
        final MatchTree result;
        if (this.lists.isEmpty()) {
            result = null;
        } else {
            if (sink != null) {
                this.estimateXor(sink);
            }
            if (this.lists.size() == 1) {
                result = new MatchTree(this.lists.get(0), this.estimate(EstimateKind.XOR));
            } else {
                result = new MatchTree(
                    new XorPostList(this.lists, this.env.dbsize(), this.env.recalc()),
                    this.estimate(EstimateKind.XOR)
                );
            }
        }
        return result;
    }

    private void shrink(final int size) {
        while (this.lists.size() > size) {
            this.lists.remove(this.lists.size() - 1);
        }
        while (this.estimates.size() > size) {
            this.estimates.remove(this.estimates.size() - 1);
        }
    }

    private void estimateOr(final TermFreqs sink) {
        final double scale = 1.0 / this.env.size();
        final double rscale = UnionContext.inverse(this.env.rset());
        final double cscale = UnionContext.inverse(this.env.total());
        final TermFreqs head = this.freqs.get(0);
        double prob = head.term() * scale;
        double rprob = head.rel() * rscale;
        double cprob = head.coll() * cscale;
        for (int index = 1; index < this.freqs.size(); ++index) {
            final TermFreqs item = this.freqs.get(index);
            final double part = item.term() * scale;
            prob += part - prob * part;
            final double cpart = item.coll() * cscale;
            cprob += cpart - cprob * cpart;
            if (this.env.rset() != 0L) {
                final double rpart = item.rel() * rscale;
                rprob += rpart - rprob * rpart;
            }
        }
        sink.assign(
            TermFreqs.round(prob * this.env.size()),
            TermFreqs.round(rprob * this.env.rset()),
            TermFreqs.round(cprob * this.env.total())
        );
    }

    private void estimateXor(final TermFreqs sink) {
        final double scale = 1.0 / this.env.size();
        final double rscale = UnionContext.inverse(this.env.rset());
        final double cscale = UnionContext.inverse(this.env.total());
        final TermFreqs head = this.freqs.get(0);
        double prob = head.term() * scale;
        double rprob = head.rel() * rscale;
        double cprob = head.coll() * cscale;
        for (int index = 1; index < this.freqs.size(); ++index) {
            final TermFreqs item = this.freqs.get(index);
            final double part = item.term() * scale;
            prob += part - 2.0 * prob * part;
            final double cpart = item.coll() * cscale;
            cprob += cpart - 2.0 * cprob * cpart;
            if (this.env.rset() != 0L) {
                final double rpart = item.rel() * rscale;
                rprob += rpart - 2.0 * rprob * rpart;
            }
        }
        sink.assign(
            TermFreqs.round(prob * this.env.size()),
            TermFreqs.round(rprob * this.env.rset()),
            TermFreqs.round(cprob * this.env.total())
        );
    }

    private MatchEstimate estimate(final EstimateKind kind) {
        return MatchEstimate.of(
            kind, new DocRange(new DocId(this.first), new DocId(this.last)),
            this.estimates
        );
    }

    private static double inverse(final long value) {
        double result = 0.0;
        if (value != 0L) {
            result = 1.0 / value;
        }
        return result;
    }
}
