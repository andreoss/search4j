/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.api.PositionIterator;
import io.github.andreoss.search4j.core.api.PostingIterator;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.NavigableMap;

/**
 * Iterator over the postings of one term.
 *
 * @since 0.1.0
 */
final class PostingCursor implements PostingIterator {

    /**
     * The store holding the documents.
     */
    private final Store store;

    /**
     * The cursor over the postings.
     */
    private final Seek<DocId, Occurrence> seek;

    /**
     * Creates an iterator over postings.
     *
     * @param store The store holding the documents
     * @param postings The postings by document
     */
    PostingCursor(
        final Store store,
        final NavigableMap<DocId, Occurrence> postings
    ) {
        this.store = store;
        this.seek = new Seek<>(
            postings, DocId.NONE, item -> true
        );
    }

    @Override
    public boolean hasNext() {
        return this.store.live() && this.seek.peek().isPresent();
    }

    @Override
    public DocId next() {
        this.store.live();
        return this.seek.advance().getKey();
    }

    @Override
    public void skipTo(final DocId target) {
        this.store.live();
        this.seek.skipTo(target);
    }

    @Override
    public TermCount wdf() {
        this.store.live();
        return this.seek.current().getValue().wdf();
    }

    @Override
    public TermCount docLength() {
        this.store.live();
        return this.store.stored(this.seek.current().getKey()).length();
    }

    @Override
    public TermCount uniqueTerms() {
        this.store.live();
        return this.store.stored(this.seek.current().getKey()).unique();
    }

    @Override
    public TermCount positionCount() {
        this.store.live();
        return new TermCount(
            this.seek.current().getValue().positions().size()
        );
    }

    @Override
    public PositionIterator positions() {
        this.store.live();
        return new PositionCursor(this.seek.current().getValue().positions());
    }

    @Override
    public void close() {
        this.seek.finish();
    }
}
