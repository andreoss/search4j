/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.backend.TermFrequencies;
import io.github.andreoss.search4j.core.backend.list.LeafPostList;
import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.backend.list.VectorPositionList;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.Optional;

/**
 * Posting list of one term of a store.
 *
 * @since 0.1.0
 */
final class InMemoryPostList extends LeafPostList {

    /**
     * The store holding the documents.
     */
    private final Store store;

    /**
     * The postings as opened.
     */
    private final Postings opened;

    /**
     * Creates the list of a term.
     *
     * @param store Store holding the documents
     * @param term Non-empty term listed
     */
    InMemoryPostList(final Store store, final String term) {
        this(
            store,
            term,
            new Postings(
                new Seek<>(store.postings(term), DocId.NONE, item -> true),
                new TermFrequencies(
                    store.frequency(term), store.collection(term)
                )
            )
        );
    }

    /**
     * Creates the list of a term from its parts.
     *
     * @param store Store holding the documents
     * @param term Non-empty term listed
     * @param opened Postings as opened
     */
    private InMemoryPostList(
        final Store store,
        final String term,
        final Postings opened
    ) {
        super(term);
        this.store = store;
        this.opened = opened;
    }

    @Override
    public DocId docId() {
        return this.opened.cursor().current().getKey();
    }

    @Override
    public boolean atEnd() {
        return this.opened.cursor().ended();
    }

    @Override
    public PostList next(final double least) {
        this.store.live();
        this.opened.cursor().step();
        return this;
    }

    @Override
    public PostList skipTo(final DocId target, final double least) {
        this.store.live();
        this.opened.cursor().seek(target);
        return this;
    }

    @Override
    public String description() {
        return String.format("InMemoryPostList(%s)", this.term());
    }

    @Override
    public TermCount termFrequency() {
        return this.opened.freqs().termFrequency();
    }

    @Override
    public TermCount wdf() {
        this.store.live();
        return this.opened.cursor().current().getValue().wdf();
    }

    @Override
    public PositionList readPositionList() {
        return this.openPositionList();
    }

    @Override
    public PositionList openPositionList() {
        this.store.live();
        return new VectorPositionList(
            this.opened.cursor().current().getValue().positions()
        );
    }

    @Override
    public TermCount collectionFrequency() {
        return this.opened.freqs().collection();
    }

    @Override
    public TermCount wdfUpperBound() {
        return this.opened.freqs().collection();
    }

    @Override
    public Optional<LeafPostList> openNearby(
        final String term,
        final boolean positions
    ) {
        return Optional.empty();
    }
}
