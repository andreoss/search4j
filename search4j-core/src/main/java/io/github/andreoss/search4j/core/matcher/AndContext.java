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
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.TotalLength;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Subqueries gathered under a conjunction, with the documents excluded, the
 * optional subqueries adding weight, the positional checks of phrase and
 * near subqueries and the frequencies of the subqueries under a synonym.
 *
 * @since 0.3.0
 */
final class AndContext {

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
     * Positional checks to apply.
     */
    private final List<PosFilter> filters;

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

    AndContext(final Matching env) {
        this.env = env;
        this.lists = new ArrayList<>(4);
        this.estimates = new ArrayList<>(4);
        this.freqs = new ArrayList<>(0);
        this.filters = new ArrayList<>(0);
        this.excluded = new UnionContext(env);
        this.optional = new UnionContext(env);
        this.first = 1;
        this.last = Integer.MAX_VALUE;
    }

    boolean add(final MatchTree tree, final TermFreqs sink) {
        if (sink != null) {
            this.freqs.add(sink.copy());
        }
        boolean result = false;
        if (tree != null) {
            if (this.lists.isEmpty() && this.freqs.size() > 1) {
                result = true;
            } else {
                this.lists.add(tree.list());
                this.estimates.add(tree.estimate());
                final DocRange range = tree.list().docIdRange(
                    new DocRange(new DocId(this.first), new DocId(this.last))
                );
                this.first = Math.max(this.first, range.first().value());
                this.last = Math.min(this.last, range.last().value());
                result = this.first <= this.last;
            }
        }
        if (!result) {
            this.clear();
            this.everything = false;
            result = sink != null;
        }
        return result;
    }

    void everything() {
        this.everything = true;
    }

    void clear() {
        this.lists.clear();
        this.estimates.clear();
    }

    void filter(final QueryOp type, final int count, final TermPos window) {
        final int end = this.lists.size();
        this.filters.add(new PosFilter(type, end - count, end, window));
    }

    UnionContext excluded() {
        return this.excluded;
    }

    UnionContext optional() {
        return this.optional;
    }

    MatchTree conjunction(final Supplier<MatchTree> all, final TermFreqs sink) {
        if (sink != null) {
            this.estimateAnd(sink);
        }
        MatchTree result = null;
        if (this.lists.isEmpty()) {
            if (this.everything) {
                result = all.get();
            }
        } else if (this.lists.size() == 1) {
            result = new MatchTree(this.lists.get(0), this.estimates.get(0));
        } else {
            result = new MatchTree(
                new AndPostList(this.lists, this.env.recalc()),
                MatchEstimate.of(EstimateKind.AND, this.range(), this.estimates)
            );
        }
        if (result != null) {
            result = this.exclude(result, sink);
            result = this.filtered(result, sink);
            if (!this.optional.empty() && this.optional.overlaps(this.first, this.last)) {
                result = new MatchTree(
                    new AndMaybePostList(
                        result.list(), this.optional.disjunction(sink, false).list(),
                        this.env.recalc()
                    ),
                    result.estimate()
                );
            }
        }
        return result;
    }

    private MatchTree exclude(final MatchTree tree, final TermFreqs sink) {
        MatchTree result = tree;
        if (!this.excluded.empty() && this.excluded.overlaps(this.first, this.last)) {
            TermFreqs right = null;
            if (sink != null) {
                right = new TermFreqs();
            }
            final MatchTree rhs = this.excluded.disjunction(right, true);
            if (sink != null) {
                this.differ(sink, right);
            }
            result = new MatchTree(
                new AndNotPostList(tree.list(), rhs.list(), this.env.dbsize()),
                MatchEstimate.of(
                    EstimateKind.AND_NOT, this.range(),
                    List.of(tree.estimate(), rhs.estimate())
                )
            );
        }
        return result;
    }

    private MatchTree filtered(final MatchTree tree, final TermFreqs sink) {
        MatchTree result = tree;
        for (final PosFilter filter : this.filters) {
            if (sink != null) {
                sink.divide(filter.kind().divisor());
            }
            result = filter.apply(result, this.lists, this.env);
        }
        return result;
    }

    private void differ(final TermFreqs sink, final TermFreqs right) {
        long coll = sink.coll();
        if (this.env.total() != 0L) {
            coll = AndContext.andNotTotal(sink.coll(), right.coll(), this.env.total());
        }
        long rel = sink.rel();
        if (this.env.rset() != 0L) {
            rel = AndContext.andNot(sink.rel(), right.rel(), this.env.rset());
        }
        sink.assign(
            AndContext.andNot(sink.term(), right.term(), this.env.size()), rel, coll
        );
    }

    private void estimateAnd(final TermFreqs sink) {
        final TermFreqs head = this.freqs.get(0);
        double freq = head.term();
        double rel = head.rel();
        double coll = head.coll();
        final long size = this.env.size();
        final long total = this.env.total();
        final long rset = this.env.rset();
        for (int index = 1; index < this.freqs.size(); ++index) {
            final TermFreqs item = this.freqs.get(index);
            final long ratio = item.term() / size;
            freq *= ratio;
            if (total != 0L) {
                final long part = Long.divideUnsigned(item.coll(), total);
                coll *= part;
            }
            if (rset != 0L) {
                final long share = item.rel() / rset;
                rel *= share;
            }
        }
        sink.assign(TermFreqs.round(freq), TermFreqs.round(rel), TermFreqs.round(coll));
    }

    private DocRange range() {
        return new DocRange(new DocId(this.first), new DocId(this.last));
    }

    private static long andNot(final long left, final long right, final long size) {
        return TermFreqs.round(left * (double) (size - right) / size);
    }

    private static long andNotTotal(final long left, final long right, final long total) {
        return TermFreqs.round(
            left * TotalLength.unsigned(total - right) / TotalLength.unsigned(total)
        );
    }
}
