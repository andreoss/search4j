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
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Wraps a union so that its documents are weighted as one term.
 *
 * @since 0.1.0
 */
public final class SynonymPostList extends WrapperPostList {

    /**
     * Scheme scoring the group as one term.
     */
    private final SynonymWeight scheme;

    /**
     * Whether the within-document frequency is clamped to the document length.
     */
    private final boolean clamp;

    /**
     * Callback run when the wrapped list is replaced.
     */
    private final Runnable recalc;

    /**
     * Creates the wrapper.
     *
     * @param wrapped Wrapped union
     * @param scheme Scheme scoring the group as one term
     * @param clamp Whether to clamp the frequency to the document length
     * @param recalculation Callback run when the wrapped list is replaced
     */
    public SynonymPostList(
        final PostList wrapped, final SynonymWeight scheme,
        final boolean clamp, final Runnable recalculation
    ) {
        this(
            SynonymPostList.prepare(wrapped, scheme, recalculation), clamp
        );
    }

    /**
     * Creates the wrapper from checked parts.
     *
     * @param parts Wrapped union and scoring scheme
     * @param clamped Whether to clamp the frequency to the document length
     */
    private SynonymPostList(final SynonymParts parts, final boolean clamped) {
        super(parts.wrapped());
        this.scheme = parts.scheme();
        this.clamp = clamped;
        this.recalc = parts.recalc();
    }

    @Override
    protected PostList move(final double least) {
        final PostList wrapped = this.wrapped();
        final PostList moved = wrapped.next(0.0);
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
        TermCount wdf = TermCount.ZERO;
        if (this.scheme.needsWdf()) {
            wdf = this.wdf();
            if (this.clamp && wdf.value() > doc.length().value()) {
                wdf = doc.length();
            }
        }
        return this.scheme.sumPart(wdf, doc);
    }

    @Override
    public double recalcMaxWeight() {
        return this.scheme.maxPart();
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

    /**
     * Validates the wrapped union and its scheme.
     *
     * @param wrapped Wrapped union
     * @param scheme Scheme scoring the group as one term
     * @param callback Callback run when the wrapped list is replaced
     * @return Checked parts
     */
    private static SynonymParts prepare(
        final PostList wrapped, final SynonymWeight scheme,
        final Runnable callback
    ) {
        if (scheme == null) {
            throw new NullPointerException("scheme");
        }
        if (callback == null) {
            throw new NullPointerException("recalculation");
        }
        return new SynonymParts(wrapped, scheme, callback);
    }

    /**
     * Wrapped union with its scoring scheme.
     *
     * @param wrapped Wrapped union
     * @param scheme Scheme scoring the group as one term
     * @param recalc Callback run when the wrapped list is replaced
     * @since 0.1.0
     */
    private record SynonymParts(
        PostList wrapped, SynonymWeight scheme, Runnable recalc
    ) {
    }
}
