/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.api.Document;
import io.github.andreoss.search4j.core.api.PositionIterator;
import io.github.andreoss.search4j.core.api.TermIterator;
import io.github.andreoss.search4j.core.api.ValueIterator;
import io.github.andreoss.search4j.core.backend.TermOrder;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Immutable copy of a document held by a store.
 *
 * @param data Opaque data
 * @param values Values by slot
 * @param terms Occurrences by term, ordered by {@link TermOrder}
 * @param length Sum of the within-document frequencies
 * @since 0.1.0
 */
record Stored(
    String data,
    NavigableMap<ValueNo, String> values,
    NavigableMap<String, Occurrence> terms,
    TermCount length
) {

    /**
     * Creates a stored document over unmodifiable views of its maps.
     *
     * @param data Opaque data
     * @param values Values by slot
     * @param terms Occurrences by term, ordered by {@link TermOrder}
     * @param length Sum of the within-document frequencies
     */
    Stored {
        values = Collections.unmodifiableNavigableMap(values);
        terms = Collections.unmodifiableNavigableMap(terms);
    }

    /**
     * Copies any document.
     *
     * @param document Document to copy
     * @return Stored copy
     */
    static Stored of(final Document document) {
        final NavigableMap<ValueNo, String> values =
            new TreeMap<>(Orders.SLOTS);
        try (ValueIterator iter = document.values()) {
            while (iter.hasNext()) {
                final String value = iter.next();
                values.put(iter.slot(), value);
            }
        }
        final NavigableMap<String, Occurrence> terms =
            new TreeMap<>(TermOrder.INSTANCE);
        TermCount length = TermCount.ZERO;
        try (TermIterator iter = document.termList()) {
            while (iter.hasNext()) {
                final String term = iter.next();
                final Occurrence occurrence = new Occurrence(
                    iter.wdf(), Stored.positions(iter)
                );
                terms.put(term, occurrence);
                length = length.plus(occurrence.wdf());
            }
        }
        return new Stored(document.data(), values, terms, length);
    }

    /**
     * Number of distinct terms, capped at the length.
     *
     * @return Distinct term count
     */
    TermCount unique() {
        return new TermCount(Math.min(this.terms.size(), this.length.value()));
    }

    /**
     * Highest within-document frequency.
     *
     * @return Highest frequency
     */
    TermCount highest() {
        return this.terms.values().stream()
            .map(Occurrence::wdf)
            .max(TermCount::compareTo)
            .orElse(TermCount.ZERO);
    }

    private static List<TermPos> positions(final TermIterator iter) {
        final List<TermPos> result = new ArrayList<>(0);
        try (PositionIterator positions = iter.positions()) {
            while (positions.hasNext()) {
                result.add(positions.next());
            }
        }
        return result;
    }
}
