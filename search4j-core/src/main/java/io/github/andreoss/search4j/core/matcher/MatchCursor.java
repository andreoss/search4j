/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Matcher core of the search engine.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.Shard;
import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Root of a posting list tree walked by a match: adopts the lists the tree
 * decays to, and recalculates the maximum weight when a list asks for it
 * before skipping documents that cannot reach a minimum weight.
 *
 * @since 0.4.0
 */
public final class MatchCursor {

    /**
     * Database shard searched.
     */
    private final Shard shard;

    /**
     * Current root of the tree.
     */
    private PostList list;

    /**
     * Estimate of the matches of the tree.
     */
    private MatchEstimate estimate;

    /**
     * Number of weighted leaf subqueries of the query.
     */
    private int subqueries;

    /**
     * Maximum weight any document can still get.
     */
    private double max;

    /**
     * Whether the maximum weight is up to date.
     */
    private boolean cached;

    /**
     * Creates a cursor with no tree.
     *
     * @param shard Database shard searched
     */
    MatchCursor(final Shard shard) {
        this.shard = shard;
    }

    /**
     * Number of weighted leaf subqueries of the query.
     *
     * @return Count, zero for an unweighted query
     */
    public int subqueries() {
        return this.subqueries;
    }

    /**
     * Highest weight any document can get, recalculated when stale.
     *
     * @return Maximum weight
     */
    public double maxWeight() {
        if (!this.cached) {
            if (this.max > 0.0) {
                this.max = Math.max(this.list.recalcMaxWeight(), 0.0);
            }
            this.cached = true;
        }
        return this.max;
    }

    /**
     * Moves to the next document which may reach a minimum weight.
     *
     * @param least Minimum weight of interest
     * @return False when no further document can match
     */
    public boolean next(final double least) {
        boolean more = !(least > 0.0 && this.maxWeight() < least);
        if (more) {
            final PostList result = this.list.next(least);
            if (result.equals(this.list)) {
                more = !result.atEnd();
            } else {
                this.list = result;
                more = !result.atEnd() && this.reachable(least);
            }
        }
        return more;
    }

    /**
     * Bounds and estimate of the matches resolved against the shard.
     *
     * @return Resolved estimates
     */
    public MatchEstimates resolved() {
        MatchEstimates result = new MatchEstimates(
            TermCount.ZERO, TermCount.ZERO, TermCount.ZERO, DocId.of(1), DocId.NONE
        );
        if (this.shard.docCount().value() != 0L) {
            final DocRange used = this.shard.usedDocIds();
            result = this.estimate.resolve(
                this.shard.docCount(), used.first(), used.last()
            );
        }
        return result;
    }

    /**
     * Document the cursor is on.
     *
     * @return Document identifier
     */
    public DocId docId() {
        return this.list.docId();
    }

    /**
     * Weight of the current document.
     *
     * @return Weight under the scheme of the query
     */
    public double weight() {
        final DocId docid = this.list.docId();
        return this.list.weight(
            new DocStats(
                this.shard.docLength(docid),
                this.shard.uniqueTerms(docid),
                this.shard.wdfDocMax(docid)
            )
        );
    }

    /**
     * Within-document frequency of the current document.
     *
     * @return Frequency summed over the matching terms
     */
    public TermCount wdf() {
        return this.list.wdf();
    }

    /**
     * Number of weighted subqueries matching the current document.
     *
     * @return Count of matching subqueries
     */
    public TermCount matchingSubqueries() {
        return this.list.countMatchingSubqueries();
    }

    /**
     * Marks the maximum weight as stale.
     */
    void stale() {
        this.cached = false;
    }

    /**
     * Installs the tree to walk and fixes its initial maximum weight.
     *
     * @param tree Posting list tree and its estimate
     * @param count Number of weighted leaf subqueries
     */
    void start(final MatchTree tree, final int count) {
        this.list = tree.list();
        this.estimate = tree.estimate();
        this.subqueries = count;
        final double initial = this.list.recalcMaxWeight();
        this.max = 0.0;
        if (0.0 < initial) {
            this.max = initial;
        }
        this.cached = true;
    }

    private boolean reachable(final double least) {
        boolean result = true;
        if (least > 0.0) {
            this.cached = false;
            result = this.maxWeight() >= least;
        }
        return result;
    }
}
