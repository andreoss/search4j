/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Posting list operators combining the streams of a query tree.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.Shard;
import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.backend.list.Probe;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.function.Consumer;

/**
 * Documents whose slot value is at or above a lower bound.
 *
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.NullAssignment",
    "PMD.ProhibitPublicStaticMethods"
})
public final class ValueGePostList implements PostList {

    /**
     * Lower-bound range underlying this list, prepared on first use.
     */
    private ValueRangePostList range;

    /**
     * Shard whose values are read.
     */
    private final Shard shard;

    /**
     * Slot read.
     */
    private final ValueNo number;

    /**
     * Lowest accepted value.
     */
    private final String bound;

    /**
     * Creates a lower-bound selection over a slot.
     *
     * @param shard Shard whose values are read
     * @param slot Slot read
     * @param lower Lowest accepted value, inclusive
     */
    public ValueGePostList(
        final Shard shard, final ValueNo slot, final String lower
    ) {
        if (shard == null) {
            throw new InvalidArgumentError("A shard is required");
        }
        if (slot == null) {
            throw new InvalidArgumentError("A slot is required");
        }
        if (lower == null) {
            throw new InvalidArgumentError("A bound is required");
        }
        this.shard = shard;
        this.number = slot;
        this.bound = lower;
        this.range = null;
    }

    /**
     * Creates a lower-bound selection over a slot.
     *
     * @param shard Shard whose values are read
     * @param slot Slot read
     * @param lower Lowest accepted value, inclusive
     * @return Lower-bound posting list
     */
    public static ValueGePostList of(
        final Shard shard, final ValueNo slot, final String lower
    ) {
        return new ValueGePostList(shard, slot, lower);
    }

    @Override
    public DocId docId() {
        return this.range().docId();
    }

    @Override
    public boolean atEnd() {
        return this.range().atEnd();
    }

    @Override
    public PostList next(final double least) {
        final PostList moved = this.range().next(least);
        PostList result = this;
        if (moved.atEnd()) {
            result = moved;
        }
        return result;
    }

    @Override
    public PostList skipTo(final DocId target, final double least) {
        final PostList moved = this.range().skipTo(target, least);
        PostList result = this;
        if (moved.atEnd()) {
            result = moved;
        }
        return result;
    }

    @Override
    public Probe check(final DocId target, final double least) {
        return new Probe(this, this.range().check(target, least).valid());
    }

    @Override
    public TermCount termFrequency() {
        return this.range().termFrequency();
    }

    @Override
    public TermCount wdf() {
        return TermCount.ZERO;
    }

    @Override
    public double weight(final DocStats doc) {
        return 0.0;
    }

    @Override
    public double recalcMaxWeight() {
        return 0.0;
    }

    @Override
    public TermCount countMatchingSubqueries() {
        return TermCount.ZERO;
    }

    @Override
    public DocRange docIdRange(final DocRange whole) {
        return whole;
    }

    @Override
    public PositionList readPositionList() {
        return this.range().readPositionList();
    }

    @Override
    public PositionList openPositionList() {
        return this.range().openPositionList();
    }

    @Override
    public void gatherPositionLists(final Consumer<PositionList> sink) {
        this.range().gatherPositionLists(sink);
    }

    @Override
    public String description() {
        return String.format(
            "ValueGePostList(%s, %s)", this.number.value(), this.bound
        );
    }

    MatchEstimates estimates() {
        return this.range().estimates();
    }

    private ValueRangePostList range() {
        if (this.range == null) {
            this.range = ValueRangePostList.atLeast(
                this.shard, this.number, this.bound
            );
        }
        return this.range;
    }
}
