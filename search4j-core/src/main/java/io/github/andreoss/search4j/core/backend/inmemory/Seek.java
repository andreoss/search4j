/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.error.InvalidOperationError;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Forward cursor over a live sorted map, tolerant of changes between steps.
 *
 * @param <K> The key type
 * @param <V> The value type
 * @since 0.1.0
 */
final class Seek<K, V> {

    /**
     * The map walked.
     */
    private final NavigableMap<K, V> map;

    /**
     * The test an entry value passes to be visited.
     */
    private final Predicate<? super V> keep;

    /**
     * The key the next entry is searched from.
     */
    private K bound;

    /**
     * Whether an entry at the bound itself is eligible.
     */
    private boolean inclusive;

    /**
     * The entry most recently returned, none before the first.
     */
    private final List<Map.Entry<K, V>> visited;

    /**
     * Whether the walk was ended.
     */
    private boolean done;

    /**
     * Creates a cursor starting at a key.
     *
     * <p>The map must carry an explicit comparator.</p>
     *
     * @param map The map walked
     * @param start The lowest key visited
     * @param keep The test an entry value passes to be visited
     */
    Seek(
        final NavigableMap<K, V> map,
        final K start,
        final Predicate<? super V> keep
    ) {
        this.map = map;
        this.keep = keep;
        this.bound = start;
        this.inclusive = true;
        this.visited = new ArrayList<>(1);
        this.done = false;
    }

    /**
     * Returns the next entry without advancing.
     *
     * @return The entry, empty at the end
     */
    Optional<Map.Entry<K, V>> peek() {
        Map.Entry<K, V> entry = null;
        if (!this.done) {
            if (this.inclusive) {
                entry = this.map.ceilingEntry(this.bound);
            } else {
                entry = this.map.higherEntry(this.bound);
            }
            while (entry != null && !this.keep.test(entry.getValue())) {
                entry = this.map.higherEntry(entry.getKey());
            }
        }
        return Optional.ofNullable(entry);
    }

    /**
     * Advances to the next entry.
     *
     * @return The entry
     */
    Map.Entry<K, V> advance() {
        final Map.Entry<K, V> entry = this.peek().orElseThrow(
            () -> new NoSuchElementException("Iteration is over")
        );
        this.bound = entry.getKey();
        this.inclusive = false;
        this.visited.clear();
        this.visited.add(entry);
        return entry;
    }

    /**
     * Moves the search start forward to a target, never backwards.
     *
     * @param target The lowest key wanted
     */
    void skipTo(final K target) {
        if (this.map.comparator().compare(target, this.bound) > 0) {
            this.bound = target;
            this.inclusive = true;
        }
    }

    /**
     * Returns the entry most recently returned.
     *
     * @return The entry
     */
    Map.Entry<K, V> current() {
        if (this.visited.isEmpty()) {
            throw new InvalidOperationError("Iteration has not started");
        }
        return this.visited.get(0);
    }

    /**
     * Ends the walk.
     */
    void finish() {
        this.done = true;
    }
}
