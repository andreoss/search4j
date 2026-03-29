/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.api.ValueIterator;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.NavigableMap;

/**
 * Iterator over the values of one slot across the documents of a store.
 *
 * @since 0.1.0
 */
final class StreamCursor implements ValueIterator {

    /**
     * The store holding the documents.
     */
    private final Store store;

    /**
     * The slot read.
     */
    private final ValueNo number;

    /**
     * The cursor over the documents holding the slot.
     */
    private final Seek<DocId, Stored> seek;

    /**
     * Creates an iterator over a slot.
     *
     * @param store The store holding the documents
     * @param docs The live documents of the store
     * @param number The slot read
     */
    StreamCursor(
        final Store store,
        final NavigableMap<DocId, Stored> docs,
        final ValueNo number
    ) {
        this.store = store;
        this.number = number;
        this.seek = new Seek<>(
            docs,
            DocId.NONE,
            stored -> stored.values().containsKey(number)
        );
    }

    @Override
    public boolean hasNext() {
        return this.store.live() && this.seek.peek().isPresent();
    }

    @Override
    public String next() {
        this.store.live();
        return this.seek.advance().getValue().values().get(this.number);
    }

    @Override
    public ValueNo slot() {
        this.store.live();
        this.seek.current();
        return this.number;
    }

    @Override
    public DocId docId() {
        this.store.live();
        return this.seek.current().getKey();
    }

    @Override
    public void skipTo(final DocId target) {
        this.store.live();
        this.seek.skipTo(target);
    }

    @Override
    public void close() {
        this.seek.finish();
    }
}
