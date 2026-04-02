/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Posting list operators combining the streams of a query tree.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.backend.list.Probe;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.List;
import java.util.function.Consumer;

/**
 * Intersection of posting lists.
 *
 * <p>Members are kept in ascending term frequency order. The rarest member
 * drives the search and the others are aligned to it.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ConfusingTernary")
public final class AndPostList implements PostList {

    /**
     * Members in ascending term frequency.
     */
    private final List<PostList> kids;

    /**
     * Highest weight each member can return.
     */
    private final double[] maxima;

    /**
     * Receiver told when the maximum weight changes.
     */
    private final Runnable recalc;

    /**
     * Estimated number of matching documents.
     */
    private final TermCount frequency;

    /**
     * Estimate tree corresponding to the query operation.
     */
    private final MatchEstimate estimate;

    /**
     * Sum of the member maximum weights.
     */
    private double total;

    /**
     * Current document, {@link DocId#NONE} before the start and at the end.
     */
    private DocId did;

    /**
     * Whether iteration has started.
     */
    private boolean began;

    /**
     * Creates an intersection.
     *
     * @param lists Members
     * @param recalculation Receiver told when the maximum weight changes
     */
    public AndPostList(
        final List<? extends PostList> lists, final Runnable recalculation
    ) {
        this(AndSetup.fresh(lists, recalculation));
    }

    /**
     * Creates an intersection from a decayed union.
     *
     * @param left First member
     * @param right Second member
     * @param lmax Highest weight the first member can return
     * @param rmax Highest weight the second member can return
     * @param recalculation Receiver told when the maximum weight changes
     * @param freq Estimate carried from the decayed union
     */
    AndPostList(
        final PostList left, final PostList right,
        final double lmax, final double rmax,
        final Runnable recalculation, final TermCount freq
    ) {
        this(
            left, right, lmax, rmax, recalculation, freq,
            MatchEstimate.intersection(List.of(left, right))
        );
    }

    /**
     * Creates an intersection while retaining its originating estimate tree.
     *
     * @param left First member
     * @param right Second member
     * @param lmax Highest weight the first member can return
     * @param rmax Highest weight the second member can return
     * @param recalculation Receiver told when the maximum weight changes
     * @param freq Estimate carried from the original operation
     * @param matching Estimate operation associated with the original query
     */
    AndPostList(
        final PostList left, final PostList right,
        final double lmax, final double rmax,
        final Runnable recalculation, final TermCount freq,
        final MatchEstimate matching
    ) {
        this(
            AndSetup.decay(
                new AndDecay(left, right, lmax, rmax), recalculation, freq
            ), matching
        );
    }

    /**
     * Creates an intersection from prepared state.
     *
     * @param setup Prepared members and estimates
     */
    private AndPostList(final AndSetup setup) {
        this(setup, MatchEstimate.intersection(setup.kids()));
    }

    /**
     * Creates an intersection from prepared state and its estimate node.
     *
     * @param setup Prepared members and estimates
     * @param matching Estimate operation associated with the original query
     */
    private AndPostList(final AndSetup setup, final MatchEstimate matching) {
        this(
            setup.kids(), setup.maxima(), setup.recalc(),
            setup.frequency(), setup.total(), matching
        );
    }

    /**
     * Creates an intersection from its parts.
     *
     * @param kids Members in ascending term frequency
     * @param maxima Highest weight each member can return
     * @param recalc Receiver told when the maximum weight changes
     * @param frequency Estimated number of matching documents
     * @param total Sum of the member maximum weights
     * @param matching Estimate operation associated with the original query
     */
    private AndPostList(
        final List<PostList> kids, final double[] maxima,
        final Runnable recalc, final TermCount frequency, final double total,
        final MatchEstimate matching
    ) {
        this.kids = kids;
        this.maxima = maxima;
        this.recalc = recalc;
        this.frequency = frequency;
        this.estimate = matching;
        this.total = total;
        this.did = DocId.NONE;
        this.began = false;
    }

    @Override
    public DocId docId() {
        return this.did;
    }

    @Override
    public boolean atEnd() {
        return this.began && !this.did.valid();
    }

    @Override
    public PostList next(final double least) {
        this.began = true;
        PostList result = this;
        if (!this.kids.isEmpty()) {
            this.advance(0, least);
            result = this.find(least);
        }
        return result;
    }

    @Override
    public PostList skipTo(final DocId target, final double least) {
        this.began = true;
        PostList result = this;
        if (!this.kids.isEmpty()) {
            this.skip(0, target, least);
            result = this.find(least);
        }
        return result;
    }

    @Override
    public TermCount termFrequency() {
        return this.frequency;
    }

    @Override
    public void gatherPositionLists(final Consumer<PositionList> sink) {
        for (final PostList kid : this.kids) {
            kid.gatherPositionLists(sink);
        }
    }

    @Override
    public double weight(final DocStats doc) {
        double result = 0.0;
        for (final PostList kid : this.kids) {
            result += kid.weight(doc);
        }
        return result;
    }

    @Override
    public double recalcMaxWeight() {
        this.total = 0.0;
        for (int idx = 0; idx < this.kids.size(); ++idx) {
            final double current = this.kids.get(idx).recalcMaxWeight();
            this.maxima[idx] = current;
            this.total += current;
        }
        return this.total;
    }

    @Override
    public TermCount wdf() {
        TermCount result = TermCount.ZERO;
        for (final PostList kid : this.kids) {
            result = result.plus(kid.wdf());
        }
        return result;
    }

    @Override
    public TermCount countMatchingSubqueries() {
        TermCount result = TermCount.ZERO;
        for (final PostList kid : this.kids) {
            result = result.plus(kid.countMatchingSubqueries());
        }
        return result;
    }

    @Override
    public DocRange docIdRange(final DocRange whole) {
        DocRange range = whole;
        if (!this.kids.isEmpty()) {
            range = this.kids.get(0).docIdRange(whole);
            for (int idx = 1; idx < this.kids.size(); ++idx) {
                range = PostListMath.narrow(
                    range, this.kids.get(idx).docIdRange(range)
                );
                if (range.last().compareTo(range.first()) < 0) {
                    break;
                }
            }
        }
        return range;
    }

    @Override
    public String description() {
        final StringBuilder text = new StringBuilder(32);
        text.append('(');
        if (!this.kids.isEmpty()) {
            text.append(this.kids.get(0).description());
            for (int idx = 1; idx < this.kids.size(); ++idx) {
                text.append(" AND ");
                text.append(this.kids.get(idx).description());
            }
        }
        text.append(')');
        return text.toString();
    }

    /**
     * Estimate operation associated with this list.
     *
     * @return Query estimate node
     */
    MatchEstimate matchEstimate() {
        return this.estimate;
    }

    private PostList find(final double least) {
        boolean more = this.step(least);
        while (more) {
            more = this.step(least);
        }
        return this;
    }

    private boolean step(final double least) {
        final boolean more;
        if (this.kids.get(0).atEnd()) {
            this.did = DocId.NONE;
            more = false;
        } else {
            this.did = this.kids.get(0).docId();
            more = this.align(least);
        }
        return more;
    }

    private boolean align(final double least) {
        boolean restart = false;
        boolean ended = false;
        for (int idx = 1; idx < this.kids.size() && !restart && !ended;
            ++idx) {
            final Probe probe = this.check(idx, this.did, least);
            if (!probe.valid()) {
                this.advance(0, least);
                restart = true;
            } else if (this.kids.get(idx).atEnd()) {
                this.did = DocId.NONE;
                ended = true;
            } else if (!this.kids.get(idx).docId().equals(this.did)) {
                this.skip(0, this.kids.get(idx).docId(), least);
                restart = true;
            }
        }
        return restart;
    }

    private double minimum(final double least, final int idx) {
        return least - (this.total - this.maxima[idx]);
    }

    private void advance(final int idx, final double least) {
        final PostList kid = this.kids.get(idx);
        final PostList result = kid.next(this.minimum(least, idx));
        if (!result.equals(kid)) {
            this.kids.set(idx, result);
            if (this.maxima[idx] > 0.0) {
                this.recalc.run();
            }
        }
    }

    private void skip(final int idx, final DocId target, final double least) {
        final PostList kid = this.kids.get(idx);
        final PostList result = kid.skipTo(
            target, this.minimum(least, idx)
        );
        if (!result.equals(kid)) {
            this.kids.set(idx, result);
            if (this.maxima[idx] > 0.0) {
                this.recalc.run();
            }
        }
    }

    private Probe check(
        final int idx, final DocId target, final double least
    ) {
        final PostList kid = this.kids.get(idx);
        final Probe probe = kid.check(target, this.minimum(least, idx));
        if (!probe.list().equals(kid)) {
            this.kids.set(idx, probe.list());
            if (this.maxima[idx] > 0.0) {
                this.recalc.run();
            }
        }
        return probe;
    }
}
