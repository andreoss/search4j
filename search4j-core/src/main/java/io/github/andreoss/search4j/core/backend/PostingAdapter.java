/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.api.PositionIterator;
import io.github.andreoss.search4j.core.api.PostingIterator;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Public posting iterator over a backend posting list.
 *
 * @since 0.1.0
 */
final class PostingAdapter implements PostingIterator {

    /**
     * The statistics of the shard listed.
     */
    private final ShardStatistics stats;

    /**
     * The look-ahead state.
     */
    private final Stage stage;

    /**
     * The list, replaced when it prunes itself.
     */
    private PostList list;

    /**
     * Creates the iterator.
     *
     * @param list Posting list, before its first document
     * @param stats Statistics of the shard listed
     */
    PostingAdapter(final PostList list, final ShardStatistics stats) {
        this.stats = stats;
        this.stage = new Stage();
        this.list = list;
    }

    @Override
    public boolean hasNext() {
        return this.stage.ready(this::step, this::end);
    }

    @Override
    public DocId next() {
        this.stage.take(this::step, this::end);
        return this.list.docId();
    }

    @Override
    public void skipTo(final DocId target) {
        this.stage.skip(
            this::step, this::end,
            () -> {
                this.list = this.list.skipTo(target);
            }
        );
    }

    @Override
    public TermCount wdf() {
        this.stage.shown();
        return this.list.wdf();
    }

    @Override
    public TermCount docLength() {
        this.stage.shown();
        return this.stats.docLength(this.list.docId());
    }

    @Override
    public TermCount uniqueTerms() {
        this.stage.shown();
        return this.stats.uniqueTerms(this.list.docId());
    }

    @Override
    public TermCount positionCount() {
        this.stage.shown();
        return this.list.openPositionList().approxSize();
    }

    @Override
    public PositionIterator positions() {
        this.stage.shown();
        return new PositionAdapter(this.list.openPositionList());
    }

    @Override
    public void close() {
        this.stage.close();
    }

    private void step() {
        this.list = this.list.next();
    }

    private boolean end() {
        return this.list.atEnd();
    }
}
