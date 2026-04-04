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
 * Posting list wrapper that supplies the merged positions of an OR.
 *
 * @since 0.1.0
 */
public final class OrPosPostList implements PostList {

    /**
     * Wrapped disjunction.
     */
    private PostList source;

    /**
     * Estimated number of matching documents.
     */
    private final TermCount frequency;

    /**
     * Creates a positional wrapper over a disjunction.
     *
     * @param disjunction Posting list whose matching leaves provide positions
     */
    public OrPosPostList(final PostList disjunction) {
        this(OrPosSetup.fresh(disjunction));
    }

    private OrPosPostList(final OrPosSetup setup) {
        this(setup.source(), setup.frequency());
    }

    private OrPosPostList(
        final PostList disjunction, final TermCount estimate
    ) {
        this.source = disjunction;
        this.frequency = estimate;
    }

    @Override
    public DocId docId() {
        return this.source.docId();
    }

    @Override
    public boolean atEnd() {
        return this.source.atEnd();
    }

    @Override
    public PostList next(final double least) {
        final PostList moved = this.source.next(least);
        final boolean replaced = !moved.equals(this.source);
        this.source = moved;
        PostList result = this;
        if (replaced) {
            result = moved;
        }
        return result;
    }

    @Override
    public PostList skipTo(final DocId target, final double least) {
        final PostList moved = this.source.skipTo(target, least);
        final boolean replaced = !moved.equals(this.source);
        this.source = moved;
        PostList result = this;
        if (replaced) {
            result = moved;
        }
        return result;
    }

    @Override
    public Probe check(final DocId target, final double least) {
        final Probe probe = this.source.check(target, least);
        this.source = probe.list();
        boolean valid = probe.valid();
        if (valid && this.source.atEnd()) {
            valid = false;
        }
        return new Probe(this, valid);
    }

    @Override
    public TermCount termFrequency() {
        return this.frequency;
    }

    @Override
    public PositionList readPositionList() {
        return OrPositionList.gather(this.source);
    }

    @Override
    public PositionList openPositionList() {
        return OrPositionList.gather(this.source);
    }

    @Override
    public void gatherPositionLists(final Consumer<PositionList> sink) {
        this.source.gatherPositionLists(sink);
    }

    @Override
    public double weight(final DocStats doc) {
        return this.source.weight(doc);
    }

    @Override
    public double recalcMaxWeight() {
        return this.source.recalcMaxWeight();
    }

    @Override
    public TermCount wdf() {
        return this.source.wdf();
    }

    @Override
    public TermCount countMatchingSubqueries() {
        return this.source.countMatchingSubqueries();
    }

    @Override
    public DocRange docIdRange(final DocRange whole) {
        return this.source.docIdRange(whole);
    }

    @Override
    public String description() {
        return String.format("OrPosPostList(%s)", this.source.description());
    }
}
