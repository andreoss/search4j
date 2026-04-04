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
     * @param recalculation Callback run when the wrapped list is replaced
     */
    public ExtraWeightPostList(
        final PostList wrapped, final ExtraWeight extra,
        final Runnable recalculation
    ) {
        this(ExtraWeightPostList.prepare(wrapped, extra, recalculation));
    }

    /**
     * Creates the wrapper from checked parts.
     *
     * @param parts Wrapped list and contribution
     */
    private ExtraWeightPostList(final ExtraParts parts) {
        super(parts.wrapped());
        this.extra = parts.extra();
        this.recalc = parts.recalc();
    }

    @Override
    protected PostList move(final double least) {
        final PostList wrapped = this.wrapped();
        final PostList moved = wrapped.next(least - this.extra.maxExtra());
        this.replace(moved);
        this.replaced(wrapped, moved);
        return moved;
    }

    @Override
    protected void replaced(final PostList before, final PostList after) {
        if (!after.equals(before)) {
            this.recalc.run();
        }
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
    public String description() {
        return String.format(
            "ExtraWeightPostList(%s)", this.wrapped().description()
        );
    }

    /**
     * Validates the wrapped list and its contribution.
     *
     * @param wrapped Wrapped list
     * @param extra Contribution added to every document
     * @param callback Callback run when the wrapped list is replaced
     * @return Checked parts
     */
    private static ExtraParts prepare(
        final PostList wrapped, final ExtraWeight extra,
        final Runnable callback
    ) {
        if (extra == null) {
            throw new NullPointerException("extra");
        }
        if (callback == null) {
            throw new NullPointerException("recalculation");
        }
        return new ExtraParts(wrapped, extra, callback);
    }

    /**
     * Wrapped list with its extra contribution.
     *
     * @param wrapped Wrapped list
     * @param extra Contribution added to every document
     * @param recalc Callback run when the wrapped list is replaced
     * @since 0.1.0
     */
    private record ExtraParts(
        PostList wrapped, ExtraWeight extra, Runnable recalc
    ) {
    }
}
