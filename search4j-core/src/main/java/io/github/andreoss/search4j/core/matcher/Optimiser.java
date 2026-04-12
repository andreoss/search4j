/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Matcher core of the search engine.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.api.query.BoundNode;
import io.github.andreoss.search4j.core.api.query.Node;
import io.github.andreoss.search4j.core.api.query.RangeNode;
import io.github.andreoss.search4j.core.api.query.ScaleNode;
import io.github.andreoss.search4j.core.backend.Shard;
import io.github.andreoss.search4j.core.backend.ValueStats;
import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.backend.list.LeafPostList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.Optional;

/**
 * Builds the posting list tree of a query and the estimate of its matches:
 * conjunctions are flattened with their exclusions and optional parts,
 * disjunctions are flattened, and scale factors reach the term weights.
 *
 * @since 0.3.0
 */
final class Optimiser {

    /**
     * Database shard searched.
     */
    private final Shard shard;

    /**
     * Weighting scheme with the collection statistics.
     */
    private final Scheme scheme;

    /**
     * Callback when maximum weights change.
     */
    private final Runnable recalc;

    /**
     * Number of weighted leaf subqueries.
     */
    private int subqueries;

    Optimiser(final Shard shard, final Scheme scheme, final Runnable recalc) {
        this.shard = shard;
        this.scheme = scheme;
        this.recalc = recalc;
    }

    int subqueries() {
        return this.subqueries;
    }

    MatchTree tree(final Node node, final double factor) {
        return switch (node.type()) {
            case LEAF_TERM, LEAF_MATCH_ALL, LEAF_MATCH_NOTHING -> this.leaf(node, factor);
            case AND, FILTER, AND_NOT, AND_MAYBE -> this.conjunction(node, factor);
            case OR -> this.disjunction(node, factor);
            case XOR -> this.exclusive(node, factor);
            case SCALE_WEIGHT -> this.tree(
                node.subquery(0).node(), factor * ((ScaleNode) node).factor()
            );
            case VALUE_RANGE, VALUE_GE, VALUE_LE -> this.value(node, factor);
            default -> throw new InvalidArgumentError(
                String.format("Query type %s is not supported", node.type())
            );
        };
    }

    private MatchTree leaf(final Node node, final double factor) {
        final MatchTree result;
        if (factor != 0.0 && node.type() != QueryOp.LEAF_MATCH_NOTHING) {
            this.subqueries = this.subqueries + 1;
        }
        if (node.type() == QueryOp.LEAF_TERM) {
            result = this.term(node, factor);
        } else if (node.type() == QueryOp.LEAF_MATCH_ALL) {
            result = this.all();
        } else {
            result = null;
        }
        return result;
    }

    private MatchTree conjunction(final Node node, final double factor) {
        final AndContext ctx = new AndContext();
        final MatchTree result;
        if (this.conjoin(ctx, node, factor)) {
            result = ctx.conjunction(this::all, this.shard.docCount(), this.recalc);
        } else {
            result = null;
        }
        return result;
    }

    private MatchTree disjunction(final Node node, final double factor) {
        final UnionContext ctx = new UnionContext();
        if (factor == 0.0) {
            this.plain(ctx, node, 0);
        } else {
            this.disjoin(ctx, node, factor, 0, true);
        }
        return ctx.disjunction(factor == 0.0, this.shard.docCount(), this.recalc);
    }

    private MatchTree exclusive(final Node node, final double factor) {
        final UnionContext ctx = new UnionContext();
        this.exclude(ctx, node, factor);
        return ctx.exclusive(this.shard.docCount(), this.recalc);
    }

    private MatchTree value(final Node node, final double factor) {
        final MatchTree result;
        if (factor != 0.0) {
            this.subqueries = this.subqueries + 1;
        }
        if (node.type() == QueryOp.VALUE_RANGE) {
            final RangeNode range = (RangeNode) node;
            result = this.range(range.slot(), range.lower(), range.upper());
        } else if (node.type() == QueryOp.VALUE_GE) {
            final BoundNode bound = (BoundNode) node;
            result = this.range(bound.slot(), bound.limit(), "");
        } else {
            final BoundNode bound = (BoundNode) node;
            result = this.range(bound.slot(), "", bound.limit());
        }
        return result;
    }

    private boolean conjoin(final AndContext ctx, final Node node, final double factor) {
        final boolean result;
        switch (node.type()) {
            case LEAF_MATCH_ALL -> {
                if (factor == 0.0) {
                    ctx.everything();
                    result = true;
                } else {
                    result = ctx.add(this.tree(node, factor));
                }
            }
            case AND, FILTER -> result = this.conjoinAll(ctx, node, factor);
            case AND_NOT -> {
                result = this.conjoin(ctx, node.subquery(0).node(), factor);
                if (result) {
                    this.plain(ctx.excluded(), node, 1);
                }
            }
            case AND_MAYBE -> {
                result = this.conjoin(ctx, node.subquery(0).node(), factor);
                if (result && factor != 0.0) {
                    this.disjoin(ctx.optional(), node, factor, 1, false);
                }
            }
            case SCALE_WEIGHT -> result = this.conjoin(
                ctx, node.subquery(0).node(), factor * ((ScaleNode) node).factor()
            );
            default -> result = ctx.add(this.tree(node, factor));
        }
        return result;
    }

    private boolean conjoinAll(final AndContext ctx, final Node node, final double factor) {
        boolean result = true;
        double scale = factor;
        for (int idx = 0; result && idx < node.subqueryCount(); idx = idx + 1) {
            result = this.conjoin(ctx, node.subquery(idx).node(), scale);
            if (node.type() == QueryOp.FILTER) {
                scale = 0.0;
            }
        }
        return result;
    }

    private void disjoin(
        final UnionContext ctx, final Node node, final double factor,
        final int from, final boolean zero
    ) {
        for (int idx = from; idx < node.subqueryCount(); idx = idx + 1) {
            final Node sub = node.subquery(idx).node();
            if (sub.type() == QueryOp.OR) {
                this.disjoin(ctx, sub, factor, 0, zero);
            } else {
                final int saved = this.subqueries;
                final MatchTree tree = this.tree(sub, factor);
                if (zero || tree == null || tree.list().recalcMaxWeight() != 0.0) {
                    ctx.add(tree);
                } else {
                    this.subqueries = saved;
                }
            }
        }
    }

    private void plain(final UnionContext ctx, final Node node, final int from) {
        for (int idx = from; idx < node.subqueryCount(); idx = idx + 1) {
            final Node sub = node.subquery(idx).node();
            if (sub.type() == QueryOp.OR) {
                this.plain(ctx, sub, 0);
            } else {
                ctx.add(this.tree(sub, 0.0));
            }
        }
    }

    private void exclude(final UnionContext ctx, final Node node, final double factor) {
        for (int idx = 0; idx < node.subqueryCount(); idx = idx + 1) {
            final Node sub = node.subquery(idx).node();
            if (sub.type() == QueryOp.XOR) {
                this.exclude(ctx, sub, factor);
            } else {
                ctx.add(this.tree(sub, factor));
            }
        }
    }

    private MatchTree term(final Node node, final double factor) {
        final String term = node.term();
        final Optional<LeafPostList> leaf = this.shard.openLeafPostList(term, false);
        MatchTree result = null;
        if (leaf.isPresent()) {
            final LeafPostList list = leaf.get();
            if (factor != 0.0) {
                list.weighting(
                    this.scheme.term(
                        Scheme.stats(list, this.shard.frequencies(term), node.wqf()),
                        factor
                    )
                );
            }
            final DocRange range = list.docIdRange(PostListMath.WHOLE);
            result = new MatchTree(
                list,
                MatchEstimate.known(list.termFrequency(), range.first(), range.last())
            );
        }
        return result;
    }

    private MatchTree all() {
        final PostList list = this.shard.openPostList("");
        return new MatchTree(
            list,
            MatchEstimate.known(
                list.termFrequency(), PostListMath.WHOLE.first(),
                PostListMath.WHOLE.last()
            )
        );
    }

    private MatchTree range(final ValueNo slot, final String begin, final String end) {
        final ValueStats stats = this.shard.valueStats(slot);
        final String lower = stats.lower();
        final String upper = stats.upper();
        final MatchTree result;
        if (lower.isEmpty() || !end.isEmpty() && end.compareTo(lower) < 0
            || begin.compareTo(upper) > 0) {
            result = null;
        } else if ((end.isEmpty() || end.compareTo(upper) >= 0)
            && begin.compareTo(lower) <= 0) {
            result = this.valued(slot, stats.frequency());
        } else {
            String high = end;
            if (end.compareTo(upper) >= 0) {
                high = "";
            }
            final ValueRangePostList list = new ValueRangePostList(
                this.shard, ValueRange.of(slot, begin, high),
                Optimiser.bounds(
                    0, RangeEstimate.frequency(stats, begin, high),
                    stats.frequency().value()
                )
            );
            result = new MatchTree(list, MatchEstimate.reported(list));
        }
        return result;
    }

    private MatchTree valued(final ValueNo slot, final TermCount freq) {
        final MatchEstimates bounds = Optimiser.bounds(freq.value(), freq.value(), freq.value());
        final MatchTree result;
        if (freq.equals(this.shard.docCount())) {
            result = new MatchTree(
                this.shard.openPostList(""), MatchEstimate.known(bounds)
            );
        } else {
            final ValueRangePostList list = new ValueRangePostList(
                this.shard, ValueRange.of(slot, "", ""), bounds
            );
            result = new MatchTree(list, MatchEstimate.reported(list));
        }
        return result;
    }

    private static MatchEstimates bounds(final long min, final long est, final long max) {
        return new MatchEstimates(
            new TermCount((int) min), new TermCount((int) est), new TermCount((int) max),
            PostListMath.WHOLE.first(), new DocId(Integer.MAX_VALUE)
        );
    }
}
