/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Matcher core of the search engine.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.api.query.BranchNode;
import io.github.andreoss.search4j.core.api.query.Node;
import io.github.andreoss.search4j.core.api.query.ScaleNode;
import io.github.andreoss.search4j.core.backend.Shard;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermPos;

/**
 * Builds the posting list tree of a query and the estimate of its matches:
 * conjunctions are flattened with their exclusions, optional parts and
 * positional checks, disjunctions are flattened and may keep an elite set,
 * synonyms are weighted as one term from estimated frequencies, wildcards
 * and edit distances expand over the terms of the shard, and scale factors
 * reach the term weights.
 *
 * @since 0.3.0
 */
final class Optimiser {

    /**
     * Shard, statistics and callback of the search.
     */
    private final Matching env;

    /**
     * Posting lists of the leaves.
     */
    private final Leaves leaves;

    /**
     * Number of weighted leaf subqueries.
     */
    private int subqueries;

    Optimiser(final Shard shard, final Scheme scheme, final Runnable recalc) {
        this(new Matching(shard, scheme.collection(), recalc), scheme);
    }

    private Optimiser(final Matching env, final Scheme scheme) {
        this.env = env;
        this.leaves = new Leaves(env, scheme);
    }

    int subqueries() {
        return this.subqueries;
    }

    MatchTree tree(final Node node, final double factor) {
        return this.postlist(node, factor, null);
    }

    private MatchTree postlist(final Node node, final double factor, final TermFreqs sink) {
        return switch (node.type()) {
            case LEAF_TERM, LEAF_MATCH_ALL -> {
                this.count(factor);
                yield this.leaves.term(node, factor, sink);
            }
            case LEAF_MATCH_NOTHING -> null;
            case AND, FILTER, AND_NOT, AND_MAYBE, PHRASE, NEAR -> this.conjunction(
                node, factor, sink
            );
            case OR, XOR, ELITE_SET, SYNONYM, MAX -> this.union(node, factor, sink);
            case SCALE_WEIGHT -> this.postlist(
                node.subquery(0).node(), factor * ((ScaleNode) node).factor(), sink
            );
            case VALUE_RANGE, VALUE_GE, VALUE_LE -> {
                this.count(factor);
                yield this.leaves.value(node, sink);
            }
            case WILDCARD, EDIT_DISTANCE -> this.expanded(node, factor, sink);
            default -> throw new InvalidArgumentError(
                String.format("Query type %s is not supported", node.type())
            );
        };
    }

    private MatchTree conjunction(final Node node, final double factor, final TermFreqs sink) {
        final AndContext ctx = new AndContext(this.env);
        MatchTree result = null;
        if (this.andLike(ctx, node, factor, sink)) {
            result = ctx.conjunction(this.leaves::all, sink);
        }
        return result;
    }

    private MatchTree union(final Node node, final double factor, final TermFreqs sink) {
        final UnionContext ctx = new UnionContext(this.env);
        return switch (node.type()) {
            case OR -> this.disjunction(ctx, node, factor, sink);
            case XOR -> {
                this.xorLike(ctx, node, factor, sink);
                yield ctx.exclusive(sink);
            }
            case ELITE_SET -> {
                this.orLike(ctx, node, factor, sink, Optimiser.parameter(node), 0, true);
                yield ctx.disjunction(sink, false);
            }
            default -> this.grouping(ctx, node, factor, sink);
        };
    }

    private MatchTree grouping(
        final UnionContext ctx, final Node node, final double factor, final TermFreqs sink
    ) {
        int saved = this.subqueries;
        if (factor != 0.0) {
            saved = saved + 1;
        }
        final MatchTree result;
        if (factor == 0.0) {
            this.boolOrLike(ctx, node, sink, 0);
            result = ctx.disjunction(sink, true);
        } else if (node.type() == QueryOp.SYNONYM) {
            final TermFreqs group = new TermFreqs();
            this.boolOrLike(ctx, node, group, 0);
            result = this.leaves.grouped(ctx.disjunction(group, true), factor, group);
        } else {
            this.orLike(ctx, node, factor, sink, 0, 0, true);
            result = ctx.maximum();
        }
        this.subqueries = saved;
        return result;
    }

    private boolean andLike(
        final AndContext ctx, final Node node, final double factor, final TermFreqs sink
    ) {
        return switch (node.type()) {
            case LEAF_MATCH_ALL -> this.everything(ctx, node, factor, sink);
            case AND, FILTER -> this.conjoin(ctx, node, factor, sink);
            case AND_NOT -> this.andLike(ctx, node.subquery(0).node(), factor, sink)
                && this.excluded(ctx, node, sink);
            case AND_MAYBE -> this.andLike(ctx, node.subquery(0).node(), factor, sink)
                && this.optional(ctx, node, factor, sink);
            case PHRASE, NEAR -> this.windowed(ctx, node, factor, sink);
            case SCALE_WEIGHT -> this.andLike(
                ctx, node.subquery(0).node(), factor * ((ScaleNode) node).factor(), sink
            );
            default -> ctx.add(this.postlist(node, factor, sink), sink);
        };
    }

    private boolean everything(
        final AndContext ctx, final Node node, final double factor, final TermFreqs sink
    ) {
        final boolean result;
        if (!this.leaves.positioned() && factor == 0.0 && sink == null) {
            ctx.everything();
            result = true;
        } else {
            result = ctx.add(this.postlist(node, factor, sink), sink);
        }
        return result;
    }

    private boolean conjoin(
        final AndContext ctx, final Node node, final double factor, final TermFreqs sink
    ) {
        boolean result = true;
        double scale = factor;
        for (int idx = 0; result && idx < node.subqueryCount(); idx = idx + 1) {
            result = this.andLike(ctx, node.subquery(idx).node(), scale, sink);
            if (node.type() == QueryOp.FILTER) {
                scale = 0.0;
            }
        }
        return result;
    }

    private boolean excluded(final AndContext ctx, final Node node, final TermFreqs sink) {
        this.boolOrLike(ctx.excluded(), node, sink, 1);
        return true;
    }

    private boolean optional(
        final AndContext ctx, final Node node, final double factor, final TermFreqs sink
    ) {
        if (factor != 0.0) {
            this.orLike(ctx.optional(), node, factor, sink, 0, 1, false);
        }
        return true;
    }

    private boolean windowed(
        final AndContext ctx, final Node node, final double factor, final TermFreqs sink
    ) {
        boolean result = false;
        if (this.env.shard().hasPositions()) {
            final boolean saved = this.leaves.positioned();
            this.leaves.positions(true);
            result = this.positional(ctx, node, factor, sink);
            if (result) {
                ctx.filter(
                    node.type(), node.subqueryCount(), new TermPos(Optimiser.parameter(node))
                );
            }
            this.leaves.positions(saved);
        } else {
            ctx.clear();
        }
        return result;
    }

    private boolean positional(
        final AndContext ctx, final Node node, final double factor, final TermFreqs sink
    ) {
        boolean result = true;
        int idx = 0;
        while (result && idx < node.subqueryCount()) {
            final Node sub = node.subquery(idx).node();
            MatchTree tree = this.postlist(sub, factor, null);
            if (tree != null && sub.type() != QueryOp.LEAF_TERM) {
                tree = new MatchTree(new OrPosPostList(tree.list()), tree.estimate());
            }
            result = ctx.add(tree, sink);
            if (result) {
                idx = idx + 1;
            }
        }
        while (!result && factor != 0.0 && idx < node.subqueryCount()) {
            this.postlist(node.subquery(idx).node(), factor, null);
            idx = idx + 1;
        }
        return result;
    }

    private MatchTree disjunction(
        final UnionContext ctx, final Node node, final double factor, final TermFreqs sink
    ) {
        final MatchTree result;
        if (factor == 0.0) {
            this.boolOrLike(ctx, node, sink, 0);
            result = ctx.disjunction(sink, true);
        } else {
            this.orLike(ctx, node, factor, sink, 0, 0, true);
            result = ctx.disjunction(sink, false);
        }
        return result;
    }

    private void orLike(
        final UnionContext ctx, final Node node, final double factor,
        final TermFreqs sink, final int elite, final int from, final boolean zero
    ) {
        final int before = ctx.size();
        for (int idx = from; idx < node.subqueryCount(); idx = idx + 1) {
            this.orSub(ctx, node.subquery(idx).node(), factor, sink, zero);
        }
        final int among = ctx.size() - before;
        if (elite != 0 && elite < among) {
            ctx.elite(elite, among);
        }
    }

    private void orSub(
        final UnionContext ctx, final Node sub, final double factor,
        final TermFreqs sink, final boolean zero
    ) {
        if (sub.type() == QueryOp.OR) {
            this.orLike(ctx, sub, factor, sink, 0, 0, zero);
        } else if (sub.type() == QueryOp.ELITE_SET) {
            this.orLike(ctx, sub, factor, sink, Optimiser.parameter(sub), 0, zero);
        } else {
            final int saved = this.subqueries;
            final MatchTree tree = this.postlist(sub, factor, sink);
            if (zero || tree == null || tree.list().recalcMaxWeight() != 0.0) {
                ctx.add(tree, sink);
            } else {
                this.subqueries = saved;
            }
        }
    }

    private void boolOrLike(
        final UnionContext ctx, final Node node, final TermFreqs sink, final int from
    ) {
        for (int idx = from; idx < node.subqueryCount(); idx = idx + 1) {
            final Node sub = node.subquery(idx).node();
            if (sub.type() == QueryOp.OR) {
                this.boolOrLike(ctx, sub, sink, 0);
            } else {
                ctx.add(this.postlist(sub, 0.0, sink), sink);
            }
        }
    }

    private void xorLike(
        final UnionContext ctx, final Node node, final double factor, final TermFreqs sink
    ) {
        for (int idx = 0; idx < node.subqueryCount(); idx = idx + 1) {
            final Node sub = node.subquery(idx).node();
            if (sub.type() == QueryOp.XOR) {
                this.xorLike(ctx, sub, factor, sink);
            } else {
                ctx.add(this.postlist(sub, factor, sink), sink);
            }
        }
    }

    private MatchTree expanded(final Node node, final double factor, final TermFreqs sink) {
        final UnionContext ctx = new UnionContext(this.env);
        final Expander expander = new Expander(this.env, this.leaves, ExpansionSpec.of(node));
        final QueryOp combiner = expander.spec().combiner();
        final MatchTree result;
        if (factor == 0.0) {
            expander.into(ctx, 0.0, sink);
            result = ctx.disjunction(sink, true);
        } else if (combiner == QueryOp.SYNONYM) {
            this.subqueries = this.subqueries + 1;
            final TermFreqs group = new TermFreqs();
            expander.into(ctx, 0.0, group);
            result = this.leaves.grouped(ctx.disjunction(group, true), factor, group);
        } else {
            expander.into(ctx, factor, sink);
            this.subqueries = this.subqueries + ctx.size();
            if (combiner == QueryOp.MAX) {
                result = ctx.maximum();
            } else {
                result = ctx.disjunction(sink, false);
            }
        }
        return result;
    }

    private void count(final double factor) {
        if (factor != 0.0) {
            this.subqueries = this.subqueries + 1;
        }
    }

    private static int parameter(final Node node) {
        return ((BranchNode) node).parameter();
    }
}
