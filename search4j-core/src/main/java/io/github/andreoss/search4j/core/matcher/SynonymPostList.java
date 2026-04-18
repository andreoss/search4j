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
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Wraps a union so that its documents are weighted as one term.
 *
 * @since 0.1.0
 */
public final class SynonymPostList extends WrapperPostList {

    /**
     * Scoring scheme of the group.
     */
    private final SynonymSpec scheme;

    /**
     * Callback run when the wrapped list is replaced.
     */
    private final Runnable recalc;

    /**
     * Creates the wrapper.
     *
     * @param wrapped Wrapped union
     * @param scheme Scheme scoring the group as one term
     * @param recalculation Callback run when the wrapped list is replaced
     */
    public SynonymPostList(
        final PostList wrapped, final SynonymSpec scheme,
        final Runnable recalculation
    ) {
        super(wrapped);
        if (scheme == null) {
            throw new InvalidArgumentError("A scheme is required");
        }
        if (recalculation == null) {
            throw new InvalidArgumentError("A callback is required");
        }
        this.scheme = scheme;
        this.recalc = recalculation;
    }

    @Override
    public TermCount wdf() {
        return this.wrapped().wdf();
    }

    @Override
    public double weight(final DocStats doc) {
        TermCount wdf = TermCount.ZERO;
        if (this.scheme.weight().needsWdf()) {
            wdf = this.wdf();
            if (this.scheme.clamp()) {
                wdf = SynonymPostList.clamped(wdf, doc);
            }
        }
        DocStats stats = doc;
        if (this.scheme.weight().needsWdfDocMax()) {
            stats = new DocStats(doc.length(), doc.unique(), doc.length());
        }
        return this.scheme.weight().sumPart(wdf, stats);
    }

    @Override
    public double recalcMaxWeight() {
        return this.scheme.weight().maxPart();
    }

    @Override
    public TermCount countMatchingSubqueries() {
        return new TermCount(1);
    }

    @Override
    public String description() {
        return String.format(
            "SynonymPostList(%s)", this.wrapped().description()
        );
    }

    @Override
    PostList move(final double least) {
        final PostList wrapped = this.wrapped();
        final PostList moved = wrapped.next(0.0);
        this.replace(moved);
        this.replaced(wrapped, moved);
        return moved;
    }

    @Override
    Probe probe(final DocId target, final double least) {
        final PostList moved = this.skip(target, least);
        PostList result = this;
        if (moved.atEnd()) {
            result = moved;
        }
        return new Probe(result, true);
    }

    @Override
    PostList skip(final DocId target, final double least) {
        final PostList wrapped = this.wrapped();
        final PostList moved = wrapped.skipTo(target, 0.0);
        this.replace(moved);
        this.replaced(wrapped, moved);
        return moved;
    }

    @Override
    void replaced(final PostList before, final PostList after) {
        if (!after.equals(before)) {
            this.recalc.run();
        }
    }

    private static TermCount clamped(final TermCount wdf, final DocStats doc) {
        final TermCount result;
        if (wdf.value() > doc.length().value()) {
            result = doc.length();
        } else {
            result = wdf;
        }
        return result;
    }
}
