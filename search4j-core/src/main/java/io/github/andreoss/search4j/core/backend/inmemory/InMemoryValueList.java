/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.backend.list.ValueList;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.ValueNo;

/**
 * Value list of one slot across the documents of a store.
 *
 * @since 0.1.0
 */
final class InMemoryValueList implements ValueList {

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
     * Creates a list over a slot.
     *
     * @param store The store holding the documents
     * @param number The slot read
     */
    InMemoryValueList(final Store store, final ValueNo number) {
        this(
            store,
            number,
            new Seek<>(
                store.docs(),
                DocId.NONE,
                stored -> stored.values().containsKey(number)
            )
        );
    }

    /**
     * Creates a list from its parts.
     *
     * @param store The store holding the documents
     * @param number The slot read
     * @param seek The cursor over the documents holding the slot
     */
    private InMemoryValueList(
        final Store store,
        final ValueNo number,
        final Seek<DocId, Stored> seek
    ) {
        this.store = store;
        this.number = number;
        this.seek = seek;
    }

    @Override
    public DocId docId() {
        this.store.live();
        return this.seek.current().getKey();
    }

    @Override
    public String value() {
        this.store.live();
        return this.seek.current().getValue().values().get(this.number);
    }

    @Override
    public ValueNo slot() {
        this.store.live();
        this.seek.current();
        return this.number;
    }

    @Override
    public boolean atEnd() {
        return this.seek.ended();
    }

    @Override
    public void next() {
        this.store.live();
        this.seek.step();
    }

    @Override
    public void skipTo(final DocId target) {
        this.store.live();
        this.seek.seek(target);
    }

    @Override
    public String description() {
        return String.format("InMemoryValueList(slot=%s)", this.number);
    }
}
