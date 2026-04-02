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
import java.util.function.Consumer;

/**
 * Union of two posting lists.
 *
 * <p>The union decays into an intersection or an optional intersection when
 * the weight threshold rules out one of the sides.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ConfusingTernary")
public final class OrPostList implements PostList {

    /**
     * Left side.
     */
    private PostList left;

    /**
     * Right side.
     */
    private PostList right;

    /**
     * Current document of the left side.
     */
    private DocId ldid;

    /**
     * Current document of the right side.
     */
    private DocId rdid;

    /**
     * Highest weight the left side can return.
     */
    private double lmax;

    /**
     * Highest weight the right side can return.
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
     * Estimate tree corresponding to the union operation.
     */
    private final MatchEstimate matching;

    /**
     * Creates a union.
     *
     * @param left Left side
     * @param right Right side
     * @param recalculation Receiver told when the maximum weight changes
     */
    public OrPostList(
        final PostList left, final PostList right,
        final Runnable recalculation
    ) {
        this(OrSetup.fresh(left, right, recalculation));
    }

    /**
     * Creates a union from prepared state.
     *
     * @param setup Prepared members and estimates
     */
    private OrPostList(final OrSetup setup) {
        this(
            setup.left(), setup.right(), setup.recalc(),
            setup.frequency(), setup.estimate()
        );
    }

    /**
     * Creates a union from its prepared parts.
     *
     * @param left Left member
     * @param right Right member
     * @param recalc Receiver told when the maximum weight changes
     * @param frequency Estimated number of matching documents
     * @param matching Query estimate node
     */
    private OrPostList(
        final PostList left, final PostList right, final Runnable recalc,
        final TermCount frequency, final MatchEstimate matching
    ) {
        this.left = left;
        this.right = right;
        this.recalc = recalc;
        this.frequency = frequency;
        this.matching = matching;
        this.ldid = DocId.NONE;
        this.rdid = DocId.NONE;
        this.lmax = 0.0;
        this.rmax = 0.0;
    }

    @Override
    public DocId docId() {
        final DocId result;
        if (!this.ldid.valid()) {
            result = this.rdid;
        } else if (!this.rdid.valid()) {
            result = this.ldid;
        } else {
            result = PostListMath.min(this.ldid, this.rdid);
        }
        return result;
    }

    @Override
    public boolean atEnd() {
        return false;
    }

    @Override
    public PostList next(final double least) {
        final PostList result;
        if (least > this.lmax) {
            result = this.decayLeftNext(least);
        } else if (least > this.rmax) {
            result = this.decayRightNext(least);
        } else {
            result = this.advanceNext(least);
        }
        return result;
    }

    @Override
    public PostList skipTo(final DocId target, final double least) {
        final boolean ladv = target.compareTo(this.ldid) > 0;
        final boolean radv = target.compareTo(this.rdid) > 0;
        final PostList result;
        if (ladv || radv) {
            result = this.skipDecide(target, least, ladv, radv);
        } else {
            result = this;
        }
        return result;
    }

    @Override
    public Probe check(final DocId target, final double least) {
        final boolean ladv = target.compareTo(this.ldid) > 0;
        final boolean radv = target.compareTo(this.rdid) > 0;
        final Probe result;
        if (ladv || radv) {
            result = this.checkDecide(target, least, ladv, radv);
        } else {
            result = new Probe(this, true);
        }
        return result;
    }

    @Override
    public TermCount termFrequency() {
        return this.frequency;
    }

    @Override
    public void gatherPositionLists(final Consumer<PositionList> sink) {
        final DocId current = this.docId();
        if (this.ldid.valid() && this.ldid.equals(current)) {
            this.left.gatherPositionLists(sink);
        }
        if (this.rdid.valid() && this.rdid.equals(current)) {
            this.right.gatherPositionLists(sink);
        }
    }

    @Override
    public double weight(final DocStats doc) {
        final double result;
        if (!this.rdid.valid() || this.ldid.compareTo(this.rdid) < 0) {
            result = this.left.weight(doc);
        } else if (!this.ldid.valid()
            || this.ldid.compareTo(this.rdid) > 0) {
            result = this.right.weight(doc);
        } else {
            result = this.left.weight(doc) + this.right.weight(doc);
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
        final TermCount result;
        if (!this.rdid.valid() || this.ldid.compareTo(this.rdid) < 0) {
            result = this.left.wdf();
        } else if (!this.ldid.valid()
            || this.ldid.compareTo(this.rdid) > 0) {
            result = this.right.wdf();
        } else {
            result = this.left.wdf().plus(this.right.wdf());
        }
        return result;
    }

    @Override
    public TermCount countMatchingSubqueries() {
        final TermCount result;
        if (!this.rdid.valid() || this.ldid.compareTo(this.rdid) < 0) {
            result = this.left.countMatchingSubqueries();
        } else if (!this.ldid.valid()
            || this.ldid.compareTo(this.rdid) > 0) {
            result = this.right.countMatchingSubqueries();
        } else {
            result = this.left.countMatchingSubqueries()
                .plus(this.right.countMatchingSubqueries());
        }
        return result;
    }

    @Override
    public DocRange docIdRange(final DocRange whole) {
        return PostListMath.union(
            this.left.docIdRange(whole), this.right.docIdRange(whole)
        );
    }

    @Override
    public String description() {
        return String.format(
            "OrPostList(%s, %s)",
            this.left.description(), this.right.description()
        );
    }

    /**
     * Estimate operation associated with this list.
     *
     * @return Query estimate node
     */
    MatchEstimate matchEstimate() {
        return this.matching;
    }

    /**
     * Estimates a union's frequency from child frequencies and ranges.
     *
     * @param left Left member
     * @param right Right member
     * @return Estimated matching document count
     */
    static TermCount estimate(final PostList left, final PostList right) {
        final DocRange lone = OrPostList.range(left);
        final DocRange rone = OrPostList.range(right);
        final long lfirst = lone.first().value();
        final long llast = lone.last().value();
        final long rfirst = rone.first().value();
        final long rlast = rone.last().value();
        final double lcount = Math.min(
            left.termFrequency().value(), (double) (llast - lfirst + 1L)
        );
        final double rcount = Math.min(
            right.termFrequency().value(), (double) (rlast - rfirst + 1L)
        );
        final TermCount result;
        if (llast < rfirst || rlast < lfirst) {
            result = PostListMath.rounded(lcount + rcount);
        } else {
            result = PostListMath.rounded(
                OrPostList.overlap(
                    lfirst, llast, lcount, rfirst, rlast, rcount
                )
            );
        }
        return result;
    }

    private PostList decayLeftNext(final double least) {
        final PostList result;
        if (least > this.rmax) {
            result = this.decayAnd(this.nextTarget(), least);
        } else {
            result = this.decayMaybe(
                this.right, this.left, this.maybeTarget(), least
            );
        }
        return result;
    }

    private PostList decayRightNext(final double least) {
        return this.decayMaybe(
            this.left, this.right, this.optionalTarget(), least
        );
    }

    private PostList advanceNext(final double least) {
        final boolean ladv = this.ldid.compareTo(this.rdid) <= 0;
        final boolean radv = this.ldid.compareTo(this.rdid) >= 0;
        if (ladv) {
            this.left = this.left.next(least - this.rmax);
        }
        if (radv) {
            this.right = this.right.next(least - this.lmax);
        }
        final boolean lend = ladv && this.left.atEnd();
        final boolean rend = radv && this.right.atEnd();
        final PostList result;
        if (lend) {
            result = this.keepRight();
        } else if (rend) {
            result = this.keepLeft();
        } else {
            this.sync(ladv, radv);
            result = this;
        }
        return result;
    }

    private PostList skipDecide(
        final DocId target, final double least,
        final boolean ladv, final boolean radv
    ) {
        final PostList result;
        if (least > this.lmax) {
            result = this.decayLeftSkip(target, least);
        } else if (least > this.rmax) {
            result = this.decayMaybe(this.left, this.right, target, least);
        } else {
            result = this.advanceSkip(target, least, ladv, radv);
        }
        return result;
    }

    private PostList decayLeftSkip(final DocId target, final double least) {
        final PostList result;
        if (least > this.rmax) {
            result = this.decayAnd(target, least);
        } else {
            result = this.decayMaybe(this.right, this.left, target, least);
        }
        return result;
    }

    private PostList advanceSkip(
        final DocId target, final double least,
        final boolean ladv, final boolean radv
    ) {
        if (ladv) {
            this.left = this.left.skipTo(target, least - this.rmax);
        }
        if (radv) {
            this.right = this.right.skipTo(target, least - this.lmax);
        }
        final boolean lend = ladv && this.left.atEnd();
        final boolean rend = radv && this.right.atEnd();
        final PostList result;
        if (lend) {
            result = this.keepRight();
        } else if (rend) {
            result = this.keepLeft();
        } else {
            this.sync(ladv, radv);
            result = this;
        }
        return result;
    }

    private Probe checkDecide(
        final DocId target, final double least,
        final boolean ladv, final boolean radv
    ) {
        final Probe result;
        if (least > this.lmax) {
            result = this.checkLeft(target, least);
        } else if (least > this.rmax) {
            result = this.decayMaybeCheck(
                this.left, this.right, target, least
            );
        } else {
            result = this.advanceCheck(target, least, ladv, radv);
        }
        return result;
    }

    private Probe checkLeft(final DocId target, final double least) {
        final Probe result;
        if (least > this.rmax) {
            result = this.decayAndCheck(target, least);
        } else {
            result = this.decayMaybeCheck(
                this.right, this.left, target, least
            );
        }
        return result;
    }

    private Probe advanceCheck(
        final DocId target, final double least,
        final boolean ladv, final boolean radv
    ) {
        final boolean lgo = this.checkLeftSide(target, least, ladv);
        final boolean rgo = this.checkRightSide(target, least, radv);
        final boolean lend = lgo && this.left.atEnd();
        final boolean rend = rgo && this.right.atEnd();
        final Probe result;
        if (lend) {
            result = new Probe(this.keepRight(), true);
        } else if (rend) {
            result = new Probe(this.keepLeft(), true);
        } else {
            this.sync(lgo, rgo);
            result = new Probe(this, this.reached(target));
        }
        return result;
    }

    private boolean checkLeftSide(
        final DocId target, final double least, final boolean ladv
    ) {
        boolean result = ladv;
        if (ladv) {
            final Probe probe = this.left.check(target, least - this.rmax);
            this.left = probe.list();
            if (!probe.valid()) {
                this.ldid = DocId.NONE;
                result = false;
            }
        }
        return result;
    }

    private boolean checkRightSide(
        final DocId target, final double least, final boolean radv
    ) {
        boolean result = radv;
        if (radv) {
            final Probe probe = this.right.check(target, least - this.lmax);
            this.right = probe.list();
            if (!probe.valid()) {
                this.rdid = DocId.NONE;
                result = false;
            }
        }
        return result;
    }

    private boolean reached(final DocId target) {
        return this.ldid.equals(target) || this.rdid.equals(target)
            || this.ldid.valid() && this.rdid.valid();
    }

    private void sync(final boolean ladv, final boolean radv) {
        if (ladv) {
            this.ldid = this.left.docId();
        }
        if (radv) {
            this.rdid = this.right.docId();
        }
    }

    private PostList keepRight() {
        this.recalc.run();
        return this.right;
    }

    private PostList keepLeft() {
        this.recalc.run();
        return this.left;
    }

    private DocId nextTarget() {
        final DocId result;
        if (this.ldid.equals(this.rdid) || !this.rdid.valid()) {
            result = this.ldid.next();
        } else if (!this.ldid.valid()) {
            result = this.rdid.next();
        } else {
            result = PostListMath.max(this.ldid, this.rdid);
        }
        return result;
    }

    private DocId maybeTarget() {
        final DocId result;
        if (!this.rdid.valid()) {
            result = this.ldid.next();
        } else if (!this.ldid.valid()
            || this.ldid.compareTo(this.rdid) >= 0) {
            result = this.rdid.next();
        } else {
            result = this.rdid;
        }
        return result;
    }

    private DocId optionalTarget() {
        final DocId result;
        if (!this.ldid.valid()) {
            result = this.rdid.next();
        } else if (!this.rdid.valid()
            || this.rdid.compareTo(this.ldid) >= 0) {
            result = this.ldid.next();
        } else {
            result = this.ldid;
        }
        return result;
    }

    private PostList decayAnd(final DocId target, final double least) {
        return this.and().skipTo(target, least);
    }

    private Probe decayAndCheck(final DocId target, final double least) {
        return this.and().check(target, least);
    }

    private AndPostList and() {
        final AndPostList and = new AndPostList(
            this.left, this.right, this.lmax, this.rmax,
            this.recalc, this.frequency, this.matching
        );
        this.left = and;
        this.recalc.run();
        return and;
    }

    private PostList decayMaybe(
        final PostList first, final PostList second,
        final DocId target, final double least
    ) {
        return this.andMaybe(first, second).skipTo(target, least);
    }

    private Probe decayMaybeCheck(
        final PostList first, final PostList second,
        final DocId target, final double least
    ) {
        return this.andMaybe(first, second).check(target, least);
    }

    private AndMaybePostList andMaybe(
        final PostList first, final PostList second
    ) {
        if (!this.left.equals(first)) {
            final double swap = this.lmax;
            this.lmax = this.rmax;
            this.rmax = swap;
        }
        final AndMaybePostList maybe = new AndMaybePostList(
            first, second, this.lmax, this.rmax, this.recalc, this.matching
        );
        this.left = maybe;
        this.recalc.run();
        return maybe;
    }

    private static double overlap(
        final long lfirst, final long llast, final double lcount,
        final long rfirst, final long rlast, final double rcount
    ) {
        double firsta = lfirst;
        double firstb = rfirst;
        double lasta = llast;
        double lastb = rlast;
        double counta = lcount;
        double countb = rcount;
        if (firsta > firstb) {
            final double swap = firsta;
            firsta = firstb;
            firstb = swap;
            final double last = lasta;
            lasta = lastb;
            lastb = last;
            final double count = counta;
            counta = countb;
            countb = count;
        }
        if (lasta > lastb) {
            firstb += lasta - lastb;
            lastb = lasta;
        }
        final double ratea = counta / (lasta - firsta + 1.0);
        final double rateb = countb / (lastb - firstb + 1.0);
        return ratea * (firstb - firsta) + rateb * (lastb - lasta)
            + (ratea + rateb - ratea * rateb) * (lasta - firstb + 1.0);
    }

    private static DocRange range(final PostList list) {
        final DocRange range = list.docIdRange(PostListMath.WHOLE);
        final DocRange result;
        if (range.last().compareTo(range.first()) < 0) {
            result = new DocRange(DocId.of(1), DocId.NONE);
        } else {
            result = range;
        }
        return result;
    }
}
