/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.backend.list.LeafPostList;
import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.error.UnimplementedError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.Optional;

/**
 * Posting list of every document of a store, each with a frequency of one.
 *
 * @since 0.1.0
 */
final class AllDocsPostList extends LeafPostList {

    /**
     * The store holding the documents.
     */
    private final Store store;

    /**
     * The cursor over the documents.
     */
    private final Seek<DocId, Stored> seek;

    /**
     * The document count when opened.
     */
    private final TermCount count;

    /**
     * Creates the list of every document.
     *
     * @param store The store holding the documents
     */
    AllDocsPostList(final Store store) {
        this(
            store,
            new Seek<>(store.docs(), DocId.NONE, item -> true),
            new TermCount(store.docs().size())
        );
    }

    /**
     * Creates the list of every document from its parts.
     *
     * @param store The store holding the documents
     * @param seek The cursor over the documents
     * @param count The document count when opened
     */
    private AllDocsPostList(
        final Store store,
        final Seek<DocId, Stored> seek,
        final TermCount count
    ) {
        super("");
        this.store = store;
        this.seek = seek;
        this.count = count;
    }

    @Override
    public DocId docId() {
        return this.seek.current().getKey();
    }

    @Override
    public boolean atEnd() {
        return this.seek.ended();
    }

    @Override
    public PostList next(final double least) {
        this.store.live();
        this.seek.step();
        return this;
    }

    @Override
    public PostList skipTo(final DocId target, final double least) {
        this.store.live();
        this.seek.seek(target);
        return this;
    }

    @Override
    public String description() {
        return String.format("AllDocsPostList(%s)", this.count);
    }

    @Override
    public TermCount termFrequency() {
        return this.count;
    }

    @Override
    public TermCount wdf() {
        this.store.live();
        this.seek.current();
        return new TermCount(1);
    }

    @Override
    public PositionList readPositionList() {
        throw new UnimplementedError(
            "Positions are not available when iterating every document"
        );
    }

    @Override
    public PositionList openPositionList() {
        throw new UnimplementedError(
            "Positions are not available when iterating every document"
        );
    }

    @Override
    public TermCount collectionFrequency() {
        return this.count;
    }

    @Override
    public TermCount wdfUpperBound() {
        return new TermCount(1);
    }

    @Override
    public Optional<LeafPostList> openNearby(
        final String term,
        final boolean positions
    ) {
        return Optional.empty();
    }
}
