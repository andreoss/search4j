/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.api.ValueIterator;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.NavigableMap;

/**
 * Iterator over the value slots of one document.
 *
 * @since 0.1.0
 */
final class SlotCursor implements ValueIterator {

    /**
     * The cursor over the slots.
     */
    private final Seek<ValueNo, String> seek;

    /**
     * Creates an iterator over slots.
     *
     * @param slots The values by slot, with an explicit comparator
     */
    SlotCursor(final NavigableMap<ValueNo, String> slots) {
        this.seek = new Seek<>(slots, new ValueNo(0L), item -> true);
    }

    @Override
    public boolean hasNext() {
        return this.seek.peek().isPresent();
    }

    @Override
    public String next() {
        return this.seek.advance().getValue();
    }

    @Override
    public ValueNo slot() {
        return this.seek.current().getKey();
    }

    @Override
    public DocId docId() {
        throw new InvalidOperationError(
            "Document identifier is not meaningful for the values of a document"
        );
    }

    @Override
    public void skipTo(final DocId target) {
        throw new InvalidOperationError(
            "Skipping by document is not meaningful for the values of a document"
        );
    }

    @Override
    public void close() {
        this.seek.finish();
    }
}
