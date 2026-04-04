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
import io.github.andreoss.search4j.core.backend.list.EmptyPostList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.backend.list.Probe;
import io.github.andreoss.search4j.core.backend.list.ValueList;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;

/**
 * Documents whose slot value falls within an inclusive lexical range.
 *
 * <p>An absent slot never matches; an empty upper bound leaves the range
 * open above. Values are compared with their natural string order.</p>
 *
 * @since 0.1.0
 */
public final class ValueRangePostList implements PostList {

    /**
     * Prepared bounds and slot.
     */
    private final ValueRange range;

    /**
     * Shard whose values are read.
     */
    private final Shard shard;

    /**
     * Values walked, absent until the first move.
     */
    private ValueList values;

    /**
     * Creates a range over a slot.
     *
     * @param shard Shard whose values are read
     * @param slot Slot read
     * @param lower Lowest accepted value, inclusive
     * @param upper Highest accepted value, inclusive, empty for no bound
     */
    public ValueRangePostList(
        final Shard shard, final ValueNo slot,
        final String lower, final String upper
    ) {
        this(
            shard, ValueRangePostList.bounds(slot, lower, upper)
        );
    }

    /**
     * Creates a range over a slot from prepared bounds.
     *
     * @param shard Shard whose values are read
     * @param range Slot and inclusive bounds
     */
    private ValueRangePostList(final Shard shard, final ValueRange range) {
        if (shard == null) {
            throw new NullPointerException("shard");
        }
        this.shard = shard;
        this.range = range;
        this.values = null;
    }

    /**
     * Creates a range over a slot.
     *
     * @param shard Shard whose values are read
     * @param slot Slot read
     * @param lower Lowest accepted value, inclusive
     * @param upper Highest accepted value, inclusive, empty for no bound
     * @return Range posting list
     */
    public static ValueRangePostList of(
        final Shard shard, final ValueNo slot,
        final String lower, final String upper
    ) {
        return new ValueRangePostList(
            shard, ValueRangePostList.bounds(slot, lower, upper)
        );
    }

    /**
     * Validates the slot and both bounds.
     *
     * @param slot Slot read
     * @param lower Lowest accepted value, inclusive
     * @param upper Highest accepted value, inclusive, empty for no bound
     * @return Checked bounds
     */
    private static ValueRange bounds(
        final ValueNo slot, final String lower, final String upper
    ) {
        return ValueRange.check(slot, lower, upper);
    }

    /**
     * Creates a range with no upper bound.
     *
     * @param shard Shard whose values are read
     * @param slot Slot read
     * @param lower Lowest accepted value, inclusive
     * @return Lower-bound posting list
     */
    public static ValueRangePostList ge(
        final Shard shard, final ValueNo slot, final String lower
    ) {
        return ValueRangePostList.of(shard, slot, lower, "");
    }

    @Override
    public DocId docId() {
        return this.values.docId();
    }

    @Override
    public boolean atEnd() {
        return this.values != null && this.values.atEnd();
    }

    @Override
    public PostList next(final double least) {
        PostList result;
        if (this.values == null) {
            this.values = this.shard.openValueList(this.range.slot());
            this.values.next();
        } else {
            this.values.next();
        }
        result = this.seek();
        return result;
    }

    @Override
    public PostList skipTo(final DocId target, final double least) {
        if (this.values == null) {
            this.values = this.shard.openValueList(this.range.slot());
        }
        this.values.skipTo(target);
        return this.seek();
    }

    @Override
    public Probe check(final DocId target, final double least) {
        if (this.values == null) {
            this.values = this.shard.openValueList(this.range.slot());
        }
        boolean valid = this.values.check(target)
            && !this.values.atEnd()
            && this.values.docId().equals(target)
            && this.range.accepts(this.values.value());
        return new Probe(this, valid);
    }

    @Override
    public TermCount termFrequency() {
        return this.shard.valueStats(this.range.slot()).frequency();
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
    public String description() {
        return this.range.describe();
    }

    /**
     * Walks to the next accepted value.
     *
     * @return This list, or the empty list once the values are exhausted
     */
    private PostList seek() {
        PostList result = this;
        boolean more = !this.values.atEnd();
        while (more && !this.range.accepts(this.values.value())) {
            this.values.next();
            more = !this.values.atEnd();
        }
        if (this.values.atEnd()) {
            result = EmptyPostList.INSTANCE;
        }
        return result;
    }

    /**
     * Slot and inclusive bounds of a value range.
     *
     * @param slot Slot read
     * @param lower Lowest accepted value, inclusive
     * @param upper Highest accepted value, inclusive, empty for no bound
     * @since 0.1.0
     */
    private record ValueRange(ValueNo slot, String lower, String upper) {

        /**
         * Validates the bounds.
         *
         * @param number Slot read
         * @param low Lowest accepted value
         * @param high Highest accepted value, empty for no bound
         * @return Checked bounds
         */
        private static ValueRange check(
            final ValueNo number, final String low, final String high
        ) {
            if (number == null) {
                throw new NullPointerException("slot");
            }
            if (low == null) {
                throw new NullPointerException("lower");
            }
            if (high == null) {
                throw new NullPointerException("upper");
            }
            if (!high.isEmpty() && low.compareTo(high) > 0) {
                throw new InvalidArgumentError(
                    String.format(
                        "Range lower bound %s exceeds upper bound %s",
                        low, high
                    )
                );
            }
            return new ValueRange(number, low, high);
        }

        /**
         * Tests whether a value is inside the range.
         *
         * @param value Candidate value
         * @return True when accepted
         */
        private boolean accepts(final String value) {
            boolean accepted = value.compareTo(this.lower) >= 0;
            if (accepted && !this.upper.isEmpty()) {
                accepted = value.compareTo(this.upper) <= 0;
            }
            return accepted;
        }

        /**
         * Human-readable description.
         *
         * @return Description naming the slot and both bounds
         */
        private String describe() {
            return String.format(
                "ValueRangePostList(%s, %s, %s)",
                this.slot.value(), this.lower, this.upper
            );
        }
    }
}
