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
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Adds a term-independent weight to every document of a wrapped list.
 *
 * @since 0.1.0
 */
public final class ExtraWeightPostList extends WrapperPostList {

    /**
     * Contribution added to every document.
     */
    private final ExtraWeight extra;

    /**
     * Callback run when the wrapped list is replaced.
     */
    private final Runnable recalc;

    /**
     * Creates the wrapper.
     *
     * @param wrapped Wrapped list
     * @param extra Contribution added to every document
     * @param recalc Callback run when the wrapped list is replaced
     */
    public ExtraWeightPostList(
        final PostList wrapped, final ExtraWeight extra,
        final Runnable recalc
    ) {
        super(wrapped);
        if (extra == null) {
            throw new InvalidArgumentError("An extra weight is required");
        }
        if (recalc == null) {
            throw new InvalidArgumentError("A callback is required");
        }
        this.extra = extra;
        this.recalc = recalc;
    }

    @Override
    public double weight(final DocStats doc) {
        return this.wrapped().weight(doc) + this.extra.sumExtra(doc);
    }

    @Override
    public double recalcMaxWeight() {
        return this.wrapped().recalcMaxWeight() + this.extra.maxExtra();
    }

    @Override
    public TermCount countMatchingSubqueries() {
        return this.wrapped().countMatchingSubqueries();
    }

    @Override
    public TermCount wdf() {
        return this.wrapped().wdf();
    }

    @Override
    public String description() {
        return String.format(
            "ExtraWeightPostList(%s)", this.wrapped().description()
        );
    }

    MatchEstimate matchEstimate() {
        return MatchEstimate.from(this.wrapped());
    }

    @Override
    PostList move(final double least) {
        final PostList wrapped = this.wrapped();
        final PostList moved = wrapped.next(least - this.extra.maxExtra());
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
}
