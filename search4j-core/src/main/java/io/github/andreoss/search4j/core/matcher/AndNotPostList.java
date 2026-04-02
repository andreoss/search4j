/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Posting list operators combining the streams of a query tree.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.backend.list.Probe;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Difference of two posting lists.
 *
 * <p>The left side is included and the right side is excluded. The left side
 * drives the iteration.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ConfusingTernary")
public final class AndNotPostList implements PostList {

    /**
     * Included side.
     */
    private PostList left;

    /**
     * Excluded side.
     */
    private PostList right;

    /**
     * Current document of the included side.
     */
    private DocId ldid;

    /**
     * Current document of the excluded side.
     */
    private DocId rdid;

    /**
     * Estimated number of matching documents.
     */
    private final TermCount frequency;

    /**
     * Estimate tree corresponding to the difference operation.
     */
    private final MatchEstimate estimate;

    /**
     * Creates a difference.
     *
     * @param left Included side
     * @param right Excluded side
     * @param dbsize Number of documents in the database
     */
    public AndNotPostList(
        final PostList left, final PostList right,
        final TermCount dbsize
    ) {
        this(AndNotSetup.fresh(left, right, dbsize));
    }

    /**
     * Creates a difference from prepared state.
     *
     * @param setup Prepared sides and estimate
     */
    private AndNotPostList(final AndNotSetup setup) {
        this(
            setup.frequency(), setup.left(), setup.right(), setup.estimate()
        );
    }

    /**
     * Creates a difference from its parts.
     *
     * @param frequency Estimated number of matching documents
     * @param left Included side
     * @param right Excluded side
     * @param matching Estimate operation associated with the query
     */
    private AndNotPostList(
        final TermCount frequency, final PostList left, final PostList right,
        final MatchEstimate matching
    ) {
        this.left = left;
        this.right = right;
        this.ldid = DocId.NONE;
        this.rdid = DocId.NONE;
        this.frequency = frequency;
        this.estimate = matching;
    }

    @Override
    public DocId docId() {
        return this.left.docId();
    }

    @Override
    public boolean atEnd() {
        return this.left.atEnd();
    }

    @Override
    public PostList next(final double least) {
        PostList result = this;
        boolean more = true;
        while (more) {
            this.left = this.left.next(least);
            if (this.left.atEnd()) {
                result = this.left;
                more = false;
            } else {
                this.ldid = this.left.docId();
                result = this.alignNext();
                more = result.equals(this) && this.ldid.equals(this.rdid);
            }
        }
        return result;
    }

    @Override
    public PostList skipTo(final DocId target, final double least) {
        final PostList result;
        if (target.compareTo(this.ldid) > 0) {
            result = this.advanceTo(target, least);
        } else {
            result = this;
        }
        return result;
    }

    @Override
    public Probe check(final DocId target, final double least) {
        final Probe probe = this.left.check(target, least);
        this.left = probe.list();
        final Probe result;
        if (probe.valid()) {
            result = this.checkAfterLeft();
        } else {
            this.ldid = DocId.NONE;
            result = new Probe(this, false);
        }
        return result;
    }

    @Override
    public TermCount termFrequency() {
        return this.frequency;
    }

    @Override
    public double weight(final DocStats doc) {
        return this.left.weight(doc);
    }

    @Override
    public double recalcMaxWeight() {
        return this.left.recalcMaxWeight();
    }

    @Override
    public TermCount wdf() {
        return this.left.wdf();
    }

    @Override
    public TermCount countMatchingSubqueries() {
        return this.left.countMatchingSubqueries();
    }

    @Override
    public String description() {
        return String.format(
            "AndNotPostList(%s, %s)",
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

    private PostList alignNext() {
        final PostList result;
        if (this.ldid.compareTo(this.rdid) > 0) {
            result = this.checkRight();
        } else {
            result = this;
        }
        return result;
    }

    private PostList advanceTo(final DocId target, final double least) {
        this.left = this.left.skipTo(target, least);
        final PostList result;
        if (this.left.atEnd()) {
            result = this.left;
        } else {
            this.ldid = this.left.docId();
            result = this.alignSkip(least);
        }
        return result;
    }

    private PostList alignSkip(final double least) {
        PostList result = this;
        if (this.ldid.compareTo(this.rdid) > 0) {
            result = this.checkRight();
        }
        if (result.equals(this) && this.ldid.equals(this.rdid)) {
            result = this.next(least);
        }
        return result;
    }

    private PostList checkRight() {
        final Probe probe = this.right.check(this.ldid, 0.0);
        this.right = probe.list();
        final PostList result;
        if (!probe.valid()) {
            result = this;
        } else if (this.right.atEnd()) {
            result = this.left;
        } else {
            this.rdid = this.right.docId();
            result = this;
        }
        return result;
    }

    private Probe checkAfterLeft() {
        Probe result = new Probe(this, true);
        if (this.left.atEnd()) {
            result = new Probe(this.left, true);
        } else {
            this.ldid = this.left.docId();
            if (this.ldid.compareTo(this.rdid) > 0) {
                result = this.checkAfterRight();
            } else if (this.ldid.equals(this.rdid)) {
                result = new Probe(this, false);
            }
        }
        return result;
    }

    private Probe checkAfterRight() {
        final Probe probe = this.right.check(this.ldid, 0.0);
        this.right = probe.list();
        Probe result = new Probe(this, true);
        if (probe.valid() && this.right.atEnd()) {
            result = new Probe(this.left, true);
        } else if (probe.valid()) {
            this.rdid = this.right.docId();
            if (this.ldid.equals(this.rdid)) {
                result = new Probe(this, false);
            }
        }
        return result;
    }
}
