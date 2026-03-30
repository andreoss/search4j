/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.backend.DocumentSource;
import io.github.andreoss.search4j.core.backend.list.TermList;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Source of a document read lazily from a store, frozen at opening.
 *
 * @param stored The stored copy of the document
 * @param store The store holding it
 * @since 0.1.0
 */
record StoredSource(Stored stored, Store store) implements DocumentSource {

    @Override
    public String data() {
        return this.stored.data();
    }

    @Override
    public NavigableMap<ValueNo, String> values() {
        final NavigableMap<ValueNo, String> result = new TreeMap<>(Orders.SLOTS);
        result.putAll(this.stored.values());
        return result;
    }

    @Override
    public Optional<String> value(final ValueNo slot) {
        return Optional.ofNullable(this.stored.values().get(slot));
    }

    @Override
    public TermList terms() {
        return new InMemoryTermList(this.stored.terms(), this.store);
    }

    @Override
    public boolean positional() {
        return this.store.positional();
    }
}
