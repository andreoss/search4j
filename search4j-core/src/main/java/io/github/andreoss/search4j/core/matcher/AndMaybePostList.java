/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Posting list operators combining the streams of a query tree.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.backend.list.Probe;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.function.Consumer;

/**
 * Optional intersection of two posting lists.
 *
 * <p>The left side is required and drives the iteration. The right side only
 * contributes weight and counts when it holds the same document.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ConfusingTernary")
public final class AndMaybePostList implements PostList {

    /**
     * Required side.
     */
    private PostList left;

    /**
     * Optional side.
     */
    private PostList right;

    /**
     * Current document of the required side.
     */
    private DocId ldid;

    /**
     * Current document of the optional side.
     */
    private DocId rdid;

    /**
     * Highest weight the required side can return.
     */
    private double lmax;

    /**
     * Highest weight the optional side can return.
     */
    private double rmax;

    /**
     * Receiver told when the maximum weight changes.
     */
    private final Runnable recalc;

    /**
     * Estimated number of matching documents.
     */
    private final TermCount frequency;

    /**
     * Estimate tree for the required side.
     */
    private final MatchEstimate estimate;

    /**
     * Creates an optional intersection.
     *
     * @param left Required side
     * @param right Optional side
     * @param recalculation Receiver told when the maximum weight changes
     */
    public AndMaybePostList(
        final PostList left, final PostList right,
        final Runnable recalculation
    ) {
        this(left, right, 0.0, 0.0, recalculation);
    }

    /**
     * Creates an optional intersection with known maximum weights.
     *
     * @param left Required side
     * @param right Optional side
     * @param lmax Highest weight the required side can return
     * @param rmax Highest weight the optional side can return
     * @param recalculation Receiver told when the maximum weight changes
     */
    AndMaybePostList(
        final PostList left, final PostList right,
        final double lmax, final double rmax,
        final Runnable recalculation
    ) {
        this(
            left, right, lmax, rmax, recalculation,
            MatchEstimate.from(left)
        );
    }

    /**
     * Creates an optional intersection retaining an originating estimate.
     *
     * @param left Required side
     * @param right Optional side
     * @param lmax Highest weight the required side can return
     * @param rmax Highest weight the optional side can return
     * @param recalculation Receiver told when the maximum weight changes
     * @param matching Estimate operation associated with the original query
     */
    AndMaybePostList(
        final PostList left, final PostList right,
        final double lmax, final double rmax,
        final Runnable recalculation, final MatchEstimate matching
    ) {
        this(
            AndMaybeSetup.fresh(
                new AndMaybeFresh(left, right, lmax, rmax, recalculation)
            ), matching
        );
    }

    /**
     * Creates an optional intersection from prepared state and its estimate.
     *
     * @param setup Prepared sides and estimates
     * @param matching Estimate operation associated with the original query
     */
    private AndMaybePostList(
        final AndMaybeSetup setup, final MatchEstimate matching
    ) {
        this(
            setup.left(), setup.right(),
            setup.lmax(), setup.rmax(),
            setup.recalc(), setup.frequency(), matching
        );
    }

    /**
     * Creates an optional intersection from its parts.
     *
     * @param left Required side
     * @param right Optional side
     * @param lmax Highest weight the required side can return
     * @param rmax Highest weight the optional side can return
     * @param recalc Receiver told when the maximum weight changes
     * @param frequency Estimated number of matching documents
     * @param matching Estimate operation associated with the original query
     */
    private AndMaybePostList(
        final PostList left, final PostList right,
        final double lmax, final double rmax,
        final Runnable recalc, final TermCount frequency,
        final MatchEstimate matching
    ) {
        this.left = left;
        this.right = right;
        this.ldid = DocId.NONE;
        this.rdid = DocId.NONE;
        this.lmax = lmax;
        this.rmax = rmax;
        this.recalc = recalc;
        this.frequency = frequency;
        this.estimate = matching;
    }

    @Override
    public DocId docId() {
        return this.ldid;
    }

    @Override
    public boolean atEnd() {
        return this.left.atEnd();
    }

    @Override
    public PostList next(final double least) {
        final PostList result;
        if (least > this.lmax) {
            result = this.decayToAnd(
                PostListMath.max(this.ldid.next(), this.rdid), least
            );
        } else {
            result = this.advance(least);
        }
        return result;
    }

    @Override
    public PostList skipTo(final DocId target, final double least) {
        final PostList result;
        if (target.compareTo(this.ldid) < 0) {
            result = this;
        } else if (least > this.lmax) {
            result = this.decayToAnd(
                PostListMath.max(target, this.rdid), least
            );
        } else {
            result = this.advanceTo(target, least);
        }
        return result;
    }

    @Override
    public Probe check(final DocId target, final double least) {
        final Probe result;
        if (least > this.lmax) {
            result = this.decayToAndCheck(
                PostListMath.max(target, this.ldid, this.rdid), least
            );
        } else {
            result = this.checkLeft(target, least);
        }
        return result;
    }

    @Override
    public TermCount termFrequency() {
        return this.frequency;
    }

    @Override
    public void gatherPositionLists(final Consumer<PositionList> sink) {
        if (!this.atEnd()) {
            this.left.gatherPositionLists(sink);
            if (this.ldid.equals(this.rdid)) {
                this.right.gatherPositionLists(sink);
            }
        }
    }

    @Override
    public double weight(final DocStats doc) {
        double result = this.left.weight(doc);
        if (this.ldid.equals(this.rdid)) {
            result += this.right.weight(doc);
        }
        return result;
    }

    @Override
    public double recalcMaxWeight() {
        this.lmax = this.left.recalcMaxWeight();
        this.rmax = this.right.recalcMaxWeight();
        return this.lmax + this.rmax;
    }

    @Override
    public TermCount wdf() {
        TermCount result = this.left.wdf();
        if (this.ldid.equals(this.rdid)) {
            result = result.plus(this.right.wdf());
        }
        return result;
    }

    @Override
    public TermCount countMatchingSubqueries() {
        TermCount result = this.left.countMatchingSubqueries();
        if (this.ldid.equals(this.rdid)) {
            result = result.plus(this.right.countMatchingSubqueries());
        }
        return result;
    }

    @Override
    public String description() {
        return String.format(
            "AndMaybePostList(%s, %s)",
            this.left.description(), this.right.description()
        );
    }

    /**
     * Estimate operation associated with this list.
     *
     * @return Query estimate node
     */
    MatchEstimate matchEstimate() {
        return this.estimate;
    }

    private PostList advance(final double least) {
        this.left = this.left.next(least - this.rmax);
        final PostList result;
        if (this.left.atEnd()) {
            this.recalc.run();
            result = this.left;
        } else {
            this.ldid = this.left.docId();
            result = this.alignNext(least);
        }
        return result;
    }

    private PostList advanceTo(final DocId target, final double least) {
        this.left = this.left.skipTo(target, least - this.rmax);
        final PostList result;
        if (this.left.atEnd()) {
            this.recalc.run();
            result = this.left;
        } else {
            this.ldid = this.left.docId();
            result = this.alignSkip();
        }
        return result;
    }

    private PostList alignNext(final double least) {
        final PostList result;
        if (this.ldid.compareTo(this.rdid) > 0) {
            result = this.checkRight(least - this.lmax);
        } else {
            result = this;
        }
        return result;
    }

    private PostList alignSkip() {
        final PostList result;
        if (this.ldid.compareTo(this.rdid) > 0) {
            result = this.checkRight(0.0);
        } else {
            result = this;
        }
        return result;
    }

    private PostList checkRight(final double least) {
        final Probe probe = this.right.check(this.ldid, least);
        this.right = probe.list();
        final PostList result;
        if (!probe.valid()) {
            result = this;
        } else if (this.right.atEnd()) {
            this.recalc.run();
            result = this.left;
        } else {
            this.rdid = this.right.docId();
            result = this;
        }
        return result;
    }

    private Probe checkLeft(final DocId target, final double least) {
        final Probe probe = this.left.check(target, least - this.rmax);
        this.left = probe.list();
        final Probe result;
        if (probe.valid()) {
            result = this.checkAfterLeft();
        } else {
            result = new Probe(this, false);
        }
        return result;
    }

    private Probe checkAfterLeft() {
        Probe result = new Probe(this, true);
        if (this.left.atEnd()) {
            this.recalc.run();
            result = new Probe(this.left, true);
        } else {
            this.ldid = this.left.docId();
            if (this.ldid.compareTo(this.rdid) > 0) {
                result = this.checkAfterRight();
            }
        }
        return result;
    }

    private Probe checkAfterRight() {
        final Probe probe = this.right.check(this.ldid, 0.0);
        this.right = probe.list();
        Probe result = new Probe(this, true);
        if (probe.valid()) {
            if (this.right.atEnd()) {
                this.recalc.run();
                result = new Probe(this.left, true);
            } else {
                this.rdid = this.right.docId();
            }
        }
        return result;
    }

    private PostList decayToAnd(final DocId target, final double least) {
        final AndPostList and = this.and();
        this.recalc.run();
        return and.skipTo(target, least);
    }

    private Probe decayToAndCheck(final DocId target, final double least) {
        final AndPostList and = this.and();
        this.recalc.run();
        return and.check(target, least);
    }

    private AndPostList and() {
        return new AndPostList(
            this.left, this.right, this.lmax, this.rmax,
            this.recalc, this.frequency, this.estimate
        );
    }
}
