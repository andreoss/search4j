/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.api.PositionIterator;
import io.github.andreoss.search4j.core.api.TermIterator;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.NavigableMap;
import java.util.NoSuchElementException;

/**
 * Iterator over the keys of a map that start with a prefix.
 *
 * @param <V> The value type
 * @since 0.1.0
 */
final class PrefixCursor<V> implements TermIterator {

    /**
     * The statistics behind the keys.
     */
    private final Stats stats;

    /**
     * The prefix every key starts with.
     */
    private final String prefix;

    /**
     * The cursor over the keys.
     */
    private final Seek<String, V> seek;

    /**
     * Creates an iterator over prefixed keys.
     *
     * @param map The map, ordered by {@link TermOrder}
     * @param prefix The prefix every key starts with
     * @param stats The statistics behind the keys
     */
    PrefixCursor(
        final NavigableMap<String, V> map,
        final String prefix,
        final Stats stats
    ) {
        this.stats = stats;
        this.prefix = prefix;
        this.seek = new Seek<>(map, prefix, item -> true);
    }

    @Override
    public boolean hasNext() {
        return this.stats.live() && this.seek.peek().filter(
            entry -> entry.getKey().startsWith(this.prefix)
        ).isPresent();
    }

    @Override
    public String next() {
        if (!this.hasNext()) {
            throw new NoSuchElementException("No more terms");
        }
        return this.seek.advance().getKey();
    }

    @Override
    public void skipTo(final String target) {
        this.stats.live();
        this.seek.skipTo(target);
    }

    @Override
    public TermCount wdf() {
        throw new InvalidOperationError(
            "Within-document frequency is not meaningful for a term listing"
        );
    }

    @Override
    public TermCount termFrequency() {
        this.stats.live();
        return this.stats.frequency(this.seek.current().getKey());
    }

    @Override
    public TermCount positionCount() {
        throw new InvalidOperationError(
            "Positions are not meaningful for a term listing"
        );
    }

    @Override
    public PositionIterator positions() {
        throw new InvalidOperationError(
            "Positions are not meaningful for a term listing"
        );
    }

    @Override
    public void close() {
        this.seek.finish();
    }
}
