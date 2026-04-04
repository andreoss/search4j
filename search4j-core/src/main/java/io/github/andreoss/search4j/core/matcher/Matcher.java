/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Matcher core of the search engine.
 */
package io.github.andreoss.search4j.core.matcher;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Query resolution: builds a PostList tree from a query.
 *
 * @since 0.1.0
 */
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
        return resolve(shard, query, new io.github.andreoss.search4j.core.api.BoolWeight());
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
        if (query.empty()) {
            return EmptyPostList.INSTANCE;
        }
        return resolveNode(shard, query.node(), weight, () -> {});
    }

    private static PostList resolveNode(
        final Shard shard, final Node node, final Weight weight,
        final Runnable recalc
    ) {
        return switch (node.type()) {
            case LEAF_TERM -> {
                final String term = node.term();
                final Optional<LeafPostList> leaf = shard.openLeafPostList(term, false);
                if (leaf.isPresent()) {
                    leaf.get().weighting(new LeafTermWeight(term, weight));
                    yield leaf.get();
                }
                yield EmptyPostList.INSTANCE;
            }
            case VALUE_RANGE -> {
                final RangeNode range = (RangeNode) node;
                yield ValueRangePostList.of(
                    shard, range.slot(), range.lower(), range.upper()
                );
            }
            case VALUE_GE -> {
                final BoundNode bound = (BoundNode) node;
                yield ValueRangePostList.ge(shard, bound.slot(), bound.limit());
            }
            case VALUE_LE -> {
                final BoundNode bound = (BoundNode) node;
                yield ValueRangePostList.of(
                    shard, bound.slot(), "", bound.limit()
                );
            }
            case LEAF_MATCH_ALL -> shard.openPostList("");
            case LEAF_MATCH_NOTHING -> EmptyPostList.INSTANCE;
            case WILDCARD, EDIT_DISTANCE -> {
                throw new InvalidArgumentError(
                    "Wildcard and edit distance not yet supported in matcher"
                );
            }
            case AND, OR, AND_NOT, XOR, AND_MAYBE -> {
                final int count = node.subqueryCount();
                final PostList[] subqueries = new PostList[count];
                for (int i = 0; i < count; i++) {
                    subqueries[i] = resolveNode(shard, node.subquery(i).node(), weight, recalc);
                }
                yield resolveOperator(shard, node.type(), subqueries, recalc);
            }
            case SCALE_WEIGHT -> {
                final ScaleNode scale = (ScaleNode) node;
                final PostList sub = resolveNode(shard, scale.subquery(0).node(), weight, recalc);
                yield new ExtraWeightPostList(sub, new SimpleExtraWeight(scale.factor()), recalc);
            }
            default -> throw new InvalidArgumentError(
                "Unknown query type: " + node.type()
            );
        };
    }

    private static PostList resolveOperator(
        final Shard shard, final QueryOp type, final PostList[] subqueries,
        final Runnable recalc
    ) {
        return switch (type) {
            case AND -> {
                yield new AndPostList(List.of(subqueries), recalc);
            }
            case OR -> {
                if (subqueries.length == 2) {
                    yield new OrPostList(subqueries[0], subqueries[1], recalc);
                } else {
                    PostList result = subqueries[0];
                    for (int i = 1; i < subqueries.length; i++) {
                        result = new OrPostList(result, subqueries[i], recalc);
                    }
                    yield result;
                }
            }
            case AND_NOT -> {
                if (subqueries.length == 2) {
                    yield new AndNotPostList(subqueries[0], subqueries[1], shard.docCount());
                } else {
                    throw new InvalidArgumentError("AND_NOT requires exactly 2 subqueries");
                }
            }
            case XOR -> {
                final List<PostList> xorLists = List.of(subqueries);
                // Need database size for XOR - use shard.docCount()
                // This is a simplification; proper implementation needs the database size
                yield new XorPostList(xorLists, shard.docCount(), recalc);
            }
            case AND_MAYBE -> {
                if (subqueries.length == 2) {
                    yield new AndMaybePostList(subqueries[0], subqueries[1], recalc);
                } else {
                    throw new InvalidArgumentError("AND_MAYBE requires exactly 2 subqueries");
                }
            }
            default -> throw new InvalidArgumentError(
                "Unhandled operator: " + type
            );
        };
    }

    /**
     * Weight adapter for a leaf term.
     */
    private static final class LeafTermWeight
        implements io.github.andreoss.search4j.core.backend.list.TermWeight {

        private final String term;
        private final Weight weight;

        LeafTermWeight(final String term, final Weight weight) {
            this.term = term;
            this.weight = weight;
        }

        @Override
        public double sumPart(
            final io.github.andreoss.search4j.core.types.TermCount wdf,
            final io.github.andreoss.search4j.core.backend.list.DocStats doc
        ) {
            return this.weight.weight(
                this.term, new io.github.andreoss.search4j.core.types.TermCount(1),
                wdf, doc.length(), doc.unique()
            );
        }

        @Override
        public double maxPart() {
            return 1.0; // Placeholder
        }
    }
}
