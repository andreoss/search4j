/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Matcher core of the search engine.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.api.BoolWeight;
import io.github.andreoss.search4j.core.api.Weight;
import io.github.andreoss.search4j.core.api.query.BoundNode;
import io.github.andreoss.search4j.core.api.query.Node;
import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.api.query.RangeNode;
import io.github.andreoss.search4j.core.api.query.ScaleNode;
import io.github.andreoss.search4j.core.backend.Shard;
import io.github.andreoss.search4j.core.backend.list.EmptyPostList;
import io.github.andreoss.search4j.core.backend.list.LeafPostList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.QueryOp;
import java.util.List;
import java.util.Optional;

/**
 * Query resolution: builds a PostList tree from a query.
 *
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.ProhibitPublicStaticMethods",
    "RefactorSwitch"
})
public final class Matcher {

    /**
     * Utility class, no instances.
     */
    private Matcher() {
    }

    /**
     * Resolves a query into a PostList using the default Boolean weight.
     *
     * @param shard Database shard
     * @param query Query to resolve
     * @return PostList tree
     */
    public static PostList resolve(final Shard shard, final Query query) {
        return Matcher.resolve(shard, query, new BoolWeight());
    }

    /**
     * Resolves a query into a PostList with a weight scheme.
     *
     * @param shard Database shard
     * @param query Query to resolve
     * @param weight Weight scheme to apply
     * @return PostList tree
     */
    public static PostList resolve(
        final Shard shard, final Query query, final Weight weight
    ) {
        final PostList result;
        if (query.empty()) {
            result = EmptyPostList.INSTANCE;
        } else {
            result = Matcher.resolveNode(
                shard, query.node(), weight, () -> {
                }
            );
        }
        return result;
    }

    private static PostList resolveNode(
        final Shard shard, final Node node, final Weight weight,
        final Runnable recalc
    ) {
        final PostList result;
        switch (node.type()) {
            case LEAF_TERM -> result = Matcher.leaf(
                shard, node.term(), weight
            );
            case LEAF_MATCH_ALL -> result = shard.openPostList("");
            case LEAF_MATCH_NOTHING -> result = EmptyPostList.INSTANCE;
            case AND, OR, AND_NOT, XOR, AND_MAYBE -> result = Matcher.branch(
                shard, node, weight, recalc
            );
            case SCALE_WEIGHT -> result = Matcher.scale(
                shard, (ScaleNode) node, weight, recalc
            );
            case VALUE_RANGE, VALUE_GE, VALUE_LE -> result = Matcher.value(
                shard, node
            );
            default -> throw new InvalidArgumentError(
                String.format(
                    "Query type %s is not supported", node.type()
                )
            );
        }
        return result;
    }

    private static PostList leaf(
        final Shard shard, final String term, final Weight weight
    ) {
        final Optional<LeafPostList> leaf =
            shard.openLeafPostList(term, false);
        final PostList result;
        if (leaf.isPresent()) {
            leaf.get().weighting(new LeafTermWeight(term, weight));
            result = leaf.get();
        } else {
            result = EmptyPostList.INSTANCE;
        }
        return result;
    }

    private static PostList value(final Shard shard, final Node node) {
        final PostList result;
        if (node.type() == QueryOp.VALUE_RANGE) {
            final RangeNode range = (RangeNode) node;
            result = ValueRangePostList.of(
                shard,
                ValueRange.of(range.slot(), range.lower(), range.upper())
            );
        } else if (node.type() == QueryOp.VALUE_GE) {
            final BoundNode bound = (BoundNode) node;
            result = ValueRangePostList.atLeast(
                shard, bound.slot(), bound.limit()
            );
        } else {
            final BoundNode bound = (BoundNode) node;
            result = ValueRangePostList.of(
                shard, ValueRange.of(bound.slot(), "", bound.limit())
            );
        }
        return result;
    }

    private static PostList branch(
        final Shard shard, final Node node, final Weight weight,
        final Runnable recalc
    ) {
        final int count = node.subqueryCount();
        final PostList[] subqueries = new PostList[count];
        int index = 0;
        while (index < count) {
            subqueries[index] = Matcher.resolveNode(
                shard, node.subquery(index).node(), weight, recalc
            );
            index = index + 1;
        }
        return Matcher.operator(shard, node.type(), subqueries, recalc);
    }

    private static PostList scale(
        final Shard shard, final ScaleNode node, final Weight weight,
        final Runnable recalc
    ) {
        return new ExtraWeightPostList(
            Matcher.resolveNode(
                shard, node.subquery(0).node(), weight, recalc
            ),
            new SimpleExtraWeight(node.factor()), recalc
        );
    }

    private static PostList operator(
        final Shard shard, final QueryOp type, final PostList[] subqueries,
        final Runnable recalc
    ) {
        final PostList result;
        switch (type) {
            case AND -> result = new AndPostList(List.of(subqueries), recalc);
            case OR -> result = Matcher.disjoin(subqueries, recalc);
            case AND_NOT -> result = Matcher.difference(subqueries, shard);
            case XOR -> result = new XorPostList(
                List.of(subqueries), shard.docCount(), recalc
            );
            case AND_MAYBE -> result = Matcher.maybe(subqueries, recalc);
            default -> throw new InvalidArgumentError(
                String.format("Operator %s is not supported", type)
            );
        }
        return result;
    }

    private static PostList disjoin(
        final PostList[] subqueries, final Runnable recalc
    ) {
        final PostList result;
        if (subqueries.length == 2) {
            result = new OrPostList(subqueries[0], subqueries[1], recalc);
        } else {
            PostList joined = subqueries[0];
            int index = 1;
            while (index < subqueries.length) {
                joined = new OrPostList(joined, subqueries[index], recalc);
                index = index + 1;
            }
            result = joined;
        }
        return result;
    }

    private static PostList difference(
        final PostList[] subqueries, final Shard shard
    ) {
        if (subqueries.length != 2) {
            throw new InvalidArgumentError(
                "AND_NOT requires exactly 2 subqueries"
            );
        }
        return new AndNotPostList(
            subqueries[0], subqueries[1], shard.docCount()
        );
    }

    private static PostList maybe(
        final PostList[] subqueries, final Runnable recalc
    ) {
        if (subqueries.length != 2) {
            throw new InvalidArgumentError(
                "AND_MAYBE requires exactly 2 subqueries"
            );
        }
        return new AndMaybePostList(subqueries[0], subqueries[1], recalc);
    }
}
