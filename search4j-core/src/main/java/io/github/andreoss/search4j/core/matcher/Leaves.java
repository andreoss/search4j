/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Matcher core of the search engine.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.api.TermStats;
import io.github.andreoss.search4j.core.api.query.BoundNode;
import io.github.andreoss.search4j.core.api.query.Node;
import io.github.andreoss.search4j.core.api.query.RangeNode;
import io.github.andreoss.search4j.core.backend.TermFrequencies;
import io.github.andreoss.search4j.core.backend.ValueStats;
import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.backend.list.LeafPostList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.backend.list.TermWeight;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.Optional;

/**
 * Posting lists of the leaves of a query: terms, every document, value
 * ranges and synonym groups weighted as one term, with the frequencies of
 * each leaf under a synonym.
 *
 * @since 0.10.0
 */
final class Leaves {

    /**
     * Shard, statistics and callback of the search.
     */
    private final Matching env;

    /**
     * Weighting scheme with the collection statistics.
     */
    private final Scheme scheme;

    /**
     * Whether term lists must read positions.
     */
    private boolean positions;

    Leaves(final Matching env, final Scheme scheme) {
        this.env = env;
        this.scheme = scheme;
    }

    void positions(final boolean wanted) {
        this.positions = wanted;
    }

    boolean positioned() {
        return this.positions;
    }

    MatchTree term(final Node node, final double factor, final TermFreqs sink) {
        final MatchTree result;
        if (node.type() == QueryOp.LEAF_TERM) {
            result = this.open(node.term(), node.wqf(), factor, sink, false);
        } else {
            if (sink != null) {
                sink.assign(this.env.everything());
            }
            result = this.all();
        }
        return result;
    }

    MatchTree expansion(final String term, final double factor) {
        return this.open(term, TermCount.ONE, factor, null, true);
    }

    MatchTree all() {
        final PostList list = this.env.shard().openPostList("");
        return new MatchTree(
            list,
            MatchEstimate.known(
                list.termFrequency(), PostListMath.WHOLE.first(),
                PostListMath.WHOLE.last()
            )
        );
    }

    MatchTree grouped(final MatchTree tree, final double factor, final TermFreqs group) {
        MatchTree result = null;
        if (tree != null) {
            final TermWeight weight = this.scheme.term(
                new TermStats(
                    new TermCount(group.term()), new TermCount(group.rel()),
                    new TermCount(group.coll()), TermCount.ONE,
                    this.env.stats().bounds().docMax()
                ),
                factor
            );
            result = new MatchTree(
                new SynonymPostList(
                    tree.list(),
                    new SynonymSpec(new GroupWeight(weight), weight.needsDocLength()),
                    this.env.recalc()
                ),
                tree.estimate()
            );
        }
        return result;
    }

    MatchTree value(final Node node, final TermFreqs sink) {
        final MatchTree result;
        if (node.type() == QueryOp.VALUE_RANGE) {
            final RangeNode range = (RangeNode) node;
            result = this.range(range.slot(), range.lower(), range.upper(), sink, true);
        } else if (node.type() == QueryOp.VALUE_GE) {
            final BoundNode bound = (BoundNode) node;
            result = this.range(bound.slot(), bound.limit(), "", sink, false);
        } else if (((BoundNode) node).limit().isEmpty()) {
            Leaves.scale(sink, 0.0);
            result = null;
        } else {
            final BoundNode bound = (BoundNode) node;
            result = this.range(bound.slot(), "", bound.limit(), sink, true);
        }
        return result;
    }

    private MatchTree open(
        final String term, final TermCount wqf, final double factor,
        final TermFreqs sink, final boolean lazy
    ) {
        final Optional<LeafPostList> leaf = this.env.shard().openLeafPostList(
            term, this.positions
        );
        final TermFrequencies freqs = this.env.shard().frequencies(term);
        MatchTree result = null;
        if (leaf.isPresent()) {
            result = this.weighed(leaf.get(), freqs, wqf, factor);
        }
        if (sink != null && !lazy) {
            sink.assign(freqs.termFrequency().value(), 0L, freqs.collection().value());
        }
        return result;
    }

    private MatchTree weighed(
        final LeafPostList list, final TermFrequencies freqs, final TermCount wqf,
        final double factor
    ) {
        if (factor != 0.0) {
            list.weighting(this.scheme.term(Scheme.stats(list, freqs, wqf), factor));
        }
        final DocRange range = list.docIdRange(PostListMath.WHOLE);
        return new MatchTree(
            list, MatchEstimate.known(list.termFrequency(), range.first(), range.last())
        );
    }

    private MatchTree range(
        final ValueNo slot, final String begin, final String end, final TermFreqs sink,
        final boolean scaled
    ) {
        final ValueStats stats = this.env.shard().valueStats(slot);
        final String lower = stats.lower();
        final String upper = stats.upper();
        final MatchTree result;
        if (lower.isEmpty() || !end.isEmpty() && end.compareTo(lower) < 0
            || begin.compareTo(upper) > 0) {
            Leaves.scale(sink, 0.0);
            result = null;
        } else if ((end.isEmpty() || end.compareTo(upper) >= 0)
            && begin.compareTo(lower) <= 0) {
            Leaves.whole(sink, this.env);
            TermFreqs share = null;
            if (scaled) {
                share = sink;
            }
            result = this.valued(slot, stats.frequency(), share);
        } else {
            Leaves.whole(sink, this.env);
            result = this.partial(stats, ValueRange.of(slot, begin, Leaves.high(end, upper)), sink);
        }
        return result;
    }

    private MatchTree partial(
        final ValueStats stats, final ValueRange range, final TermFreqs sink
    ) {
        final long guess = RangeEstimate.frequency(stats, range.lower(), range.upper());
        Leaves.scale(sink, (double) guess / this.env.dbsize().value());
        final ValueRangePostList list = new ValueRangePostList(
            this.env.shard(), range,
            Leaves.bounds(0, guess, stats.frequency().value())
        );
        return new MatchTree(list, MatchEstimate.reported(list));
    }

    private MatchTree valued(final ValueNo slot, final TermCount freq, final TermFreqs sink) {
        final MatchEstimates bounds = Leaves.bounds(freq.value(), freq.value(), freq.value());
        final MatchTree result;
        if (freq.equals(this.env.dbsize())) {
            result = new MatchTree(
                this.env.shard().openPostList(""), MatchEstimate.known(bounds)
            );
        } else {
            Leaves.scale(sink, (double) freq.value() / this.env.dbsize().value());
            final ValueRangePostList list = new ValueRangePostList(
                this.env.shard(), ValueRange.of(slot, "", ""), bounds
            );
            result = new MatchTree(list, MatchEstimate.reported(list));
        }
        return result;
    }

    private static String high(final String end, final String upper) {
        String result = end;
        if (end.compareTo(upper) >= 0) {
            result = "";
        }
        return result;
    }

    private static void whole(final TermFreqs sink, final Matching env) {
        if (sink != null) {
            sink.assign(env.everything());
        }
    }

    private static void scale(final TermFreqs sink, final double factor) {
        if (sink != null) {
            sink.scale(factor);
        }
    }

    private static MatchEstimates bounds(final long min, final long est, final long max) {
        return new MatchEstimates(
            new TermCount(min), new TermCount(est), new TermCount(max),
            PostListMath.WHOLE.first(), new DocId(Integer.MAX_VALUE)
        );
    }
}
