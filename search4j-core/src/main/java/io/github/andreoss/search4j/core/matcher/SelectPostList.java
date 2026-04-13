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
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.List;
import java.util.function.Consumer;

/**
 * Filters a candidate stream while preserving its weights and postings.
 *
 * @since 0.1.0
 */
public abstract class SelectPostList implements PostList {

    /**
     * Candidate stream containing every required term.
     */
    private PostList source;

    /**
     * Positional children in query order.
     */
    private final List<PostList> terms;

    /**
     * Estimated number of selected documents.
     */
    private final TermCount frequency;

    /**
     * Positional window, absent for exact phrase matching.
     */
    private final TermPos window;

    /**
     * Creates a positional filter from prepared state.
     *
     * @param setup Validated source, children and estimate
     */
    protected SelectPostList(final SelectSetup setup) {
        this(
            setup.source(), setup.terms(), setup.frequency(), setup.window()
        );
    }

    private SelectPostList(
        final PostList candidates,
        final List<PostList> children,
        final TermCount estimate,
        final TermPos span
    ) {
        this.source = candidates;
        this.terms = children;
        this.frequency = estimate;
        this.window = span;
    }

    @Override
    public final DocId docId() {
        return this.source.docId();
    }

    @Override
    public final boolean atEnd() {
        return this.source.atEnd();
    }

    @Override
    public final PostList next(final double least) {
        if (!this.source.atEnd()) {
            this.source = this.source.next(least);
            while (!this.source.atEnd() && !this.testDoc()) {
                this.source = this.source.next(least);
            }
        }
        PostList result = this;
        if (this.source.atEnd()) {
            result = this.source;
        }
        return result;
    }

    @Override
    public final PostList skipTo(final DocId target, final double least) {
        if (!this.source.atEnd()
            && target.compareTo(this.source.docId()) > 0) {
            this.source = this.source.skipTo(target, least);
            while (!this.source.atEnd() && !this.testDoc()) {
                this.source = this.source.next(least);
            }
        }
        PostList result = this;
        if (this.source.atEnd()) {
            result = this.source;
        }
        return result;
    }

    @Override
    public final Probe check(final DocId target, final double least) {
        final Probe probe = this.source.check(target, least);
        this.source = probe.list();
        return new Probe(this, probe.valid() && this.testCurrent());
    }

    @Override
    public final TermCount termFrequency() {
        return this.frequency;
    }

    @Override
    public final double weight(final DocStats doc) {
        return this.source.weight(doc);
    }

    @Override
    public final double recalcMaxWeight() {
        return this.source.recalcMaxWeight();
    }

    @Override
    public abstract TermCount wdf();

    @Override
    public final TermCount countMatchingSubqueries() {
        return this.source.countMatchingSubqueries();
    }

    @Override
    public final DocRange docIdRange(final DocRange whole) {
        return this.source.docIdRange(whole);
    }

    @Override
    public final void gatherPositionLists(final Consumer<PositionList> sink) {
        this.source.gatherPositionLists(sink);
    }

    /**
     * Positional children in their original query order.
     *
     * @return Immutable positional children
     */
    protected final List<PostList> terms() {
        return this.terms;
    }

    /**
     * Candidate stream currently wrapped by the positional filter.
     *
     * @return Current candidate stream
     */
    protected final PostList source() {
        return this.source;
    }

    /**
     * Position window configured for the operator.
     *
     * @return Window, or null for exact phrase matching
     */
    protected final TermPos window() {
        return this.window;
    }

    /**
     * Minimum within-document frequency among the positional children.
     *
     * @return Conservative frequency estimate
     */
    protected final TermCount minimumWdf() {
        long minimum = TermCount.MAX.value();
        for (final PostList term : this.terms) {
            minimum = Math.min(minimum, term.wdf().value());
        }
        return new TermCount(minimum);
    }

    /**
     * Tests positional membership at the source's current document.
     *
     * @return True when the current candidate passes the filter
     */
    abstract boolean testDoc();

    private boolean testCurrent() {
        return !this.source.atEnd() && this.testDoc();
    }
}
