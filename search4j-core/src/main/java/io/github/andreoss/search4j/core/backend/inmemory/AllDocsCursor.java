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
import io.github.andreoss.search4j.core.error.UnimplementedError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.NavigableMap;

/**
 * Iterator over every document of a store, each with a frequency of one.
 *
 * @since 0.1.0
 */
final class AllDocsCursor implements PostingIterator {

    /**
     * The store holding the documents.
     */
    private final Store store;

    /**
     * The cursor over the documents.
     */
    private final Seek<DocId, Stored> seek;

    /**
     * Creates an iterator over every document.
     *
     * @param store The store holding the documents
     * @param docs The live documents of the store
     */
    AllDocsCursor(final Store store, final NavigableMap<DocId, Stored> docs) {
        this.store = store;
        this.seek = new Seek<>(docs, DocId.NONE, item -> true);
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
        this.seek.current();
        return new TermCount(1);
    }

    @Override
    public TermCount docLength() {
        this.store.live();
        return this.seek.current().getValue().length();
    }

    @Override
    public TermCount uniqueTerms() {
        this.store.live();
        return this.seek.current().getValue().unique();
    }

    @Override
    public TermCount positionCount() {
        throw new UnimplementedError(
            "Positions are not available when iterating every document"
        );
    }

    @Override
    public PositionIterator positions() {
        throw new UnimplementedError(
            "Positions are not available when iterating every document"
        );
    }

    @Override
    public void close() {
        this.seek.finish();
    }
}
