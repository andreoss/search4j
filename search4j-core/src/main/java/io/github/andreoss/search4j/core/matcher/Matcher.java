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
import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.backend.Shard;
import io.github.andreoss.search4j.core.backend.list.EmptyPostList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Query resolution: builds a PostList tree from a query.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
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
        return Matcher.build(shard, query, weight).list();
    }

    /**
     * Resolves a query into a PostList tree and the estimate of its matches.
     *
     * @param shard Database shard
     * @param query Query to resolve
     * @param weight Weight scheme to apply
     * @return Tree and estimate
     */
    public static MatchTree build(
        final Shard shard, final Query query, final Weight weight
    ) {
        return Matcher.build(shard, query, weight, new MatchCursor(shard));
    }

    /**
     * Prepares a match: builds the posting list tree of a query on a cursor
     * that recalculates maximum weights whenever the tree changes shape.
     *
     * @param shard Database shard
     * @param query Query to match
     * @param weight Weight scheme to apply
     * @return Cursor positioned before the first match
     */
    public static MatchCursor cursor(
        final Shard shard, final Query query, final Weight weight
    ) {
        final MatchCursor cursor = new MatchCursor(shard);
        Matcher.build(shard, query, weight, cursor);
        return cursor;
    }

    private static MatchTree build(
        final Shard shard, final Query query, final Weight weight,
        final MatchCursor cursor
    ) {
        MatchTree tree = null;
        int count = 0;
        if (!query.empty() && shard.docCount().value() > 0) {
            final Runnable recalc = cursor::stale;
            final Scheme scheme = Scheme.of(weight, shard, query);
            double factor = 1.0;
            if (weight instanceof BoolWeight) {
                factor = 0.0;
            }
            final Optimiser optimiser = new Optimiser(shard, scheme, recalc);
            tree = optimiser.tree(query.node(), factor);
            count = optimiser.subqueries();
            final ExtraWeight extra = scheme.extra();
            if (tree != null && extra.maxExtra() != 0.0) {
                tree = new MatchTree(
                    new ExtraWeightPostList(tree.list(), extra, recalc),
                    tree.estimate()
                );
            }
        }
        if (tree == null) {
            tree = new MatchTree(
                EmptyPostList.INSTANCE,
                MatchEstimate.known(TermCount.ZERO, DocId.of(1), DocId.NONE)
            );
        }
        cursor.start(tree, count);
        return tree;
    }
}
