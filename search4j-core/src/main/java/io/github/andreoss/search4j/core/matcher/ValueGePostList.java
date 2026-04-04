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
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.function.Consumer;

/**
 * Documents whose slot value is at or above a lower bound.
 *
 * @since 0.1.0
 */
public final class ValueGePostList implements PostList {

    /**
     * Lower-bound range underlying this list.
     */
    private final ValueRangePostList range;

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
        this(ValueGePostList.checked(shard, slot, lower));
    }

    /**
     * Creates a lower-bound selection from a checked range.
     *
     * @param parts Slot, bound and underlying range
     */
    private ValueGePostList(final LowerParts parts) {
        this.range = parts.range();
        this.number = parts.slot();
        this.bound = parts.bound();
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

    /**
     * Validates the bound and prepares the underlying range.
     *
     * @param shard Shard whose values are read
     * @param slot Slot read
     * @param lower Lowest accepted value, inclusive
     * @return Checked parts
     */
    private static LowerParts checked(
        final Shard shard, final ValueNo slot, final String lower
    ) {
        if (lower == null) {
            throw new NullPointerException("lower");
        }
        return new LowerParts(
            slot, lower, ValueRangePostList.ge(shard, slot, lower)
        );
    }

    /**
     * Slot, bound and underlying range.
     *
     * @param slot Slot read
     * @param bound Lowest accepted value
     * @param range Underlying lower-bound range
     * @since 0.1.0
     */
    private record LowerParts(
        ValueNo slot, String bound, ValueRangePostList range
    ) {
    }

    @Override
    public DocId docId() {
        return this.range.docId();
    }

    @Override
    public boolean atEnd() {
        return this.range.atEnd();
    }

    @Override
    public PostList next(final double least) {
        final PostList moved = this.range.next(least);
        PostList result = this;
        if (moved.atEnd()) {
            result = moved;
        }
        return result;
    }

    @Override
    public PostList skipTo(final DocId target, final double least) {
        final PostList moved = this.range.skipTo(target, least);
        PostList result = this;
        if (moved.atEnd()) {
            result = moved;
        }
        return result;
    }

    @Override
    public Probe check(final DocId target, final double least) {
        return new Probe(this, this.range.check(target, least).valid());
    }

    @Override
    public TermCount termFrequency() {
        return this.range.termFrequency();
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
        return this.range.readPositionList();
    }

    @Override
    public PositionList openPositionList() {
        return this.range.openPositionList();
    }

    @Override
    public void gatherPositionLists(final Consumer<PositionList> sink) {
        this.range.gatherPositionLists(sink);
    }

    @Override
    public String description() {
        return String.format(
            "ValueGePostList(%s, %s)", this.number.value(), this.bound
        );
    }
}
