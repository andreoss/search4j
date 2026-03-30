/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.error.InvalidOperationError;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Forward cursor over a live sorted map, tolerant of changes between steps.
 *
 * <p>The cursor starts before its first entry and ends once a move finds
 * none.</p>
 *
 * @param <K> The key type
 * @param <V> The value type
 * @since 0.1.0
 */
final class Seek<K, V> {

    /**
     * The map walked, with an explicit comparator.
     */
    private final NavigableMap<K, V> map;

    /**
     * The test an entry value passes to be visited.
     */
    private final Predicate<? super V> keep;

    /**
     * The lowest key visited.
     */
    private final K start;

    /**
     * The entry the cursor is on, empty before the first.
     */
    private Optional<Map.Entry<K, V>> current;

    /**
     * Whether the walk is over.
     */
    private boolean ended;

    /**
     * Creates a cursor starting at a key.
     *
     * @param map The map walked, with an explicit comparator
     * @param start The lowest key visited
     * @param keep The test an entry value passes to be visited
     */
    Seek(
        final NavigableMap<K, V> map,
        final K start,
        final Predicate<? super V> keep
    ) {
        this(map, start, keep, Optional.empty());
    }

    /**
     * Creates a cursor on an entry.
     *
     * @param map The map walked, with an explicit comparator
     * @param start The lowest key visited
     * @param keep The test an entry value passes to be visited
     * @param current The entry the cursor is on, empty before the first
     */
    private Seek(
        final NavigableMap<K, V> map,
        final K start,
        final Predicate<? super V> keep,
        final Optional<Map.Entry<K, V>> current
    ) {
        this.map = map;
        this.keep = keep;
        this.start = start;
        this.current = current;
        this.ended = false;
    }

    /**
     * Moves to the next entry, or ends the walk when there is none.
     */
    void step() {
        if (!this.ended) {
            final Map.Entry<K, V> from;
            if (this.current.isPresent()) {
                from = this.map.higherEntry(this.current.get().getKey());
            } else {
                from = this.map.ceilingEntry(this.start);
            }
            this.land(from);
        }
    }

    /**
     * Moves to the first entry at or after a target, never backwards.
     *
     * @param target The lowest key wanted
     */
    void seek(final K target) {
        if (!this.ended && this.current.map(
            entry -> this.map.comparator().compare(target, entry.getKey()) > 0
        ).orElse(true)) {
            K from = target;
            if (this.map.comparator().compare(this.start, target) > 0) {
                from = this.start;
            }
            this.land(this.map.ceilingEntry(from));
        }
    }

    /**
     * Entry the cursor is on.
     *
     * @return Entry, the last one reached once ended
     */
    Map.Entry<K, V> current() {
        return this.current.orElseThrow(
            () -> new InvalidOperationError("Iteration has not started")
        );
    }

    /**
     * Tests whether the walk is over.
     *
     * @return True once a move found no entry or the walk was ended
     */
    boolean ended() {
        return this.ended;
    }

    /**
     * Ends the walk.
     */
    void finish() {
        this.ended = true;
    }

    private void land(final Map.Entry<K, V> from) {
        Map.Entry<K, V> entry = from;
        while (entry != null && !this.keep.test(entry.getValue())) {
            entry = this.map.higherEntry(entry.getKey());
        }
        if (entry == null) {
            this.ended = true;
        } else {
            this.current = Optional.of(entry);
        }
    }
}
