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
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.List;
import java.util.function.Consumer;

/**
 * Filters a candidate stream while preserving its weights and postings.
 *
 * <p>A candidate whose weight is below the minimum asked for is skipped
 * without being tested; the numbers of tested candidates accepted and
 * rejected refine the estimate of the matches.</p>
 *
 * @since 0.1.0
 */
public abstract class SelectPostList implements PostList {

    /**
     * Candidate stream containing every required term.
     */
    private PostList source;

    /**
     * Children, estimate, window and document statistics of the filter.
     */
    private final SelectSetup setup;

    /**
     * Weight of the current candidate, negative when not calculated.
     */
    private double cached;

    /**
     * Whether the candidate stream was started.
     */
    private boolean began;

    /**
     * Whether the candidate stream ran out.
     */
    private boolean ended;

    /**
     * Number of tested candidates accepted.
     */
    private long accepted;

    /**
     * Number of tested candidates rejected.
     */
    private long rejected;

    /**
     * Creates a positional filter from prepared state.
     *
     * @param setup Validated source, children and estimate
     */
    protected SelectPostList(final SelectSetup setup) {
        this(setup, setup.source());
    }

    private SelectPostList(final SelectSetup setup, final PostList source) {
        this.setup = setup;
        this.source = source;
        this.cached = Double.NEGATIVE_INFINITY;
    }

    @Override
    public final DocId docId() {
        return this.source.docId();
    }

    @Override
    public final boolean atEnd() {
        return this.ended;
    }

    @Override
    public final PostList next(final double least) {
        this.began = true;
        do {
            this.source = this.source.next(least);
        } while (!this.vet(least));
        return this;
    }

    @Override
    public final PostList skipTo(final DocId target, final double least) {
        DocId current = DocId.NONE;
        if (this.began) {
            current = this.source.docId();
        }
        PostList result = this;
        if (target.compareTo(current) > 0) {
            this.began = true;
            this.source = this.source.skipTo(target, least);
            if (!this.vet(least)) {
                result = this.next(least);
            }
        }
        return result;
    }

    @Override
    public final Probe check(final DocId target, final double least) {
        this.began = true;
        final Probe probe = this.source.check(target, least);
        this.source = probe.list();
        boolean valid = probe.valid();
        if (valid) {
            valid = this.vet(least);
        }
        return new Probe(this, valid);
    }

    @Override
    public final TermCount termFrequency() {
        return this.setup.frequency();
    }

    @Override
    public final double weight(final DocStats doc) {
        double result = this.cached;
        if (result < 0.0) {
            result = this.source.weight(doc);
        }
        return result;
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
    public final PositionList readPositionList() {
        return this.source.readPositionList();
    }

    @Override
    public final void gatherPositionLists(final Consumer<PositionList> sink) {
        this.source.gatherPositionLists(sink);
    }

    /**
     * Positional children in their current order.
     *
     * @return Positional children
     */
    protected final List<PostList> terms() {
        return this.setup.terms();
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
        return this.setup.window();
    }

    /**
     * Smallest within-document frequency of the positional children.
     *
     * @return Minimum frequency
     */
    protected final TermCount minimumWdf() {
        final List<PostList> terms = this.setup.terms();
        TermCount minimum = terms.get(0).wdf();
        for (int index = 1; index < terms.size(); ++index) {
            final TermCount wdf = terms.get(index).wdf();
            if (wdf.compareTo(minimum) < 0) {
                minimum = wdf;
            }
        }
        return minimum;
    }

    /**
     * Number of tested candidates accepted and rejected.
     *
     * @return Accepted count as the minimum, rejected count as the maximum
     */
    final MatchEstimates ratio() {
        return new MatchEstimates(
            TermCount.wrap(this.accepted), TermCount.ZERO, TermCount.wrap(this.rejected),
            PostListMath.WHOLE.first(), PostListMath.WHOLE.last()
        );
    }

    abstract boolean testDoc();

    private boolean vet(final double least) {
        boolean result = true;
        if (this.source.atEnd()) {
            this.ended = true;
        } else {
            boolean tested = true;
            this.cached = Double.NEGATIVE_INFINITY;
            if (least > 0.0 && this.setup.stats() != null) {
                this.cached = this.source.weight(
                    this.setup.stats().apply(this.source.docId())
                );
                tested = this.cached >= least;
            }
            result = tested && this.tested();
        }
        return result;
    }

    private boolean tested() {
        final boolean result = this.testDoc();
        if (result) {
            this.accepted = this.accepted + 1L;
        } else {
            this.rejected = this.rejected + 1L;
        }
        return result;
    }
}
