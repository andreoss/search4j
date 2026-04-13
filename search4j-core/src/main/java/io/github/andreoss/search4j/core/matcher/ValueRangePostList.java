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
@SuppressWarnings({
    "PMD.ProhibitPublicStaticMethods",
    "PMD.NullAssignment"
})
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
     * Static bounds and estimate of the matches.
     */
    private final MatchEstimates bounds;

    /**
     * Number of documents found in the range.
     */
    private int accepted;

    /**
     * Number of documents with a value outside the range.
     */
    private int rejected;

    /**
     * First document holding a value, none until the first move.
     */
    private DocId opened;

    /**
     * Creates a range over a prepared slot and bounds.
     *
     * @param shard Shard whose values are read
     * @param range Slot and inclusive bounds
     */
    public ValueRangePostList(final Shard shard, final ValueRange range) {
        this(shard, range, ValueRangePostList.estimated(shard, range));
    }

    ValueRangePostList(
        final Shard shard, final ValueRange range, final MatchEstimates bounds
    ) {
        this.shard = shard;
        this.range = range;
        this.bounds = bounds;
        this.values = null;
        this.opened = DocId.NONE;
    }

    /**
     * Creates a range over a slot.
     *
     * @param shard Shard whose values are read
     * @param range Slot and inclusive bounds
     * @return Range posting list
     */
    public static ValueRangePostList of(
        final Shard shard, final ValueRange range
    ) {
        return new ValueRangePostList(shard, range);
    }

    /**
     * Creates a range with no upper bound.
     *
     * @param shard Shard whose values are read
     * @param slot Slot read
     * @param lower Lowest accepted value, inclusive
     * @return Lower-bound posting list
     */
    public static ValueRangePostList atLeast(
        final Shard shard, final ValueNo slot, final String lower
    ) {
        return new ValueRangePostList(
            shard, ValueRange.of(slot, lower, "")
        );
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
        final boolean fresh = this.values == null;
        this.begin();
        this.values.next();
        if (fresh) {
            this.opened = this.values.docId();
            if (this.values.atEnd()) {
                this.opened = this.shard.lastDocId().next();
            }
        }
        return this.seek();
    }

    @Override
    public PostList skipTo(final DocId target, final double least) {
        this.begin();
        this.values.skipTo(target);
        return this.seek();
    }

    @Override
    public Probe check(final DocId target, final double least) {
        this.begin();
        boolean valid = this.values.check(target)
            && !this.values.atEnd()
            && this.values.docId().equals(target);
        if (valid) {
            valid = this.range.accepts(this.values.value());
            this.count(valid);
        }
        return new Probe(this, valid);
    }

    @Override
    public TermCount termFrequency() {
        return this.bounds.est();
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
        return new TermCount(1);
    }

    @Override
    public DocRange docIdRange(final DocRange whole) {
        return whole;
    }

    @Override
    public String description() {
        return this.range.describe();
    }

    MatchEstimates estimates() {
        return this.bounds;
    }

    /**
     * Estimates refined by the documents seen so far: the documents found
     * become the lower bound, the documents rejected leave the upper bound,
     * and the estimate blends the static one with the rate seen.
     *
     * @return Bounds and estimate of the matches
     */
    MatchEstimates reported() {
        final MatchEstimates fixed = this.bounds;
        DocId lower = fixed.first();
        if (this.opened.valid()) {
            lower = this.opened;
        }
        long min = fixed.min().value();
        long est = fixed.est().value();
        long max = fixed.max().value();
        if ((this.accepted != 0 || this.rejected != 0) && min != max) {
            min = this.accepted;
            final double rate = (double) (max - this.accepted - this.rejected)
                / max * est;
            est = this.accepted + (long) (rate + 0.5);
            max = max - this.rejected;
        }
        return new MatchEstimates(
            new TermCount(min), new TermCount(est),
            new TermCount(max), lower, fixed.last()
        );
    }

    private static MatchEstimates estimated(
        final Shard shard, final ValueRange range
    ) {
        if (shard == null) {
            throw new InvalidArgumentError("A shard is required");
        }
        if (range == null) {
            throw new InvalidArgumentError("A range is required");
        }
        return RangeEstimate.of(
            shard.valueStats(range.slot()), range.lower(), range.upper()
        );
    }

    private void begin() {
        if (this.values == null) {
            this.values = this.shard.openValueList(this.range.slot());
        }
    }

    private PostList seek() {
        PostList result = this;
        boolean more = !this.values.atEnd();
        while (more) {
            final boolean inside = this.range.accepts(this.values.value());
            this.count(inside);
            if (inside) {
                more = false;
            } else {
                this.values.next();
                more = !this.values.atEnd();
            }
        }
        if (this.values.atEnd()) {
            result = EmptyPostList.INSTANCE;
        }
        return result;
    }

    private void count(final boolean inside) {
        if (inside) {
            this.accepted = this.accepted + 1;
        } else {
            this.rejected = this.rejected + 1;
        }
    }
}
