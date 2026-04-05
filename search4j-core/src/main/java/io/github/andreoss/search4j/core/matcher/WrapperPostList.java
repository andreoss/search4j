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
 * Base for a posting list that delegates every operation to another.
 *
 * <p>Subclasses change behaviour through the protected hooks instead of
 * overriding the public operations.</p>
 *
 * @since 0.1.0
 */
public abstract class WrapperPostList implements PostList {

    /**
     * Wrapped list.
     */
    private PostList source;

    /**
     * Creates a wrapper over a list.
     *
     * @param wrapped List to delegate to
     */
    protected WrapperPostList(final PostList wrapped) {
        this.source = wrapped;
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
        final PostList moved = this.move(least);
        PostList result = this;
        if (moved.atEnd()) {
            result = moved;
        }
        return result;
    }

    @Override
    public final PostList skipTo(final DocId target, final double least) {
        final PostList wrapped = this.source;
        final PostList moved = wrapped.skipTo(target, least);
        this.replaced(wrapped, moved);
        this.source = moved;
        PostList result = this;
        if (moved.atEnd()) {
            result = moved;
        }
        return result;
    }

    @Override
    public final Probe check(final DocId target, final double least) {
        final Probe probe = this.source.check(target, least);
        this.source = probe.list();
        return new Probe(this, probe.valid());
    }

    @Override
    public final TermCount termFrequency() {
        return this.source.termFrequency();
    }

    @Override
    public abstract TermCount wdf();

    @Override
    public abstract double weight(DocStats doc);

    @Override
    public abstract double recalcMaxWeight();

    @Override
    public abstract TermCount countMatchingSubqueries();

    @Override
    public final DocRange docIdRange(final DocRange whole) {
        return this.source.docIdRange(whole);
    }

    @Override
    public final PositionList readPositionList() {
        return this.source.readPositionList();
    }

    @Override
    public final PositionList openPositionList() {
        return this.source.openPositionList();
    }

    @Override
    public final void gatherPositionLists(final Consumer<PositionList> sink) {
        this.source.gatherPositionLists(sink);
    }

    @Override
    public abstract String description();

    /**
     * List currently wrapped.
     *
     * @return Current inner list
     */
    protected final PostList wrapped() {
        return this.source;
    }

    /**
     * Replaces the wrapped list after an interior move.
     *
     * @param replacement List to carry on with
     */
    protected final void replace(final PostList replacement) {
        this.source = replacement;
    }

    /**
     * Moves the wrapped list and returns the list to carry on with.
     *
     * @param least Lowest weight contribution still wanted
     * @return This wrapper when still in use, otherwise the exhausted list
     */
    PostList move(final double least) {
        final PostList wrapped = this.source;
        final PostList moved = wrapped.next(least);
        this.replaced(wrapped, moved);
        this.source = moved;
        return moved;
    }

    abstract void replaced(PostList before, PostList after);
}
