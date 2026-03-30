/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.backend.list.AllTermsList;
import io.github.andreoss.search4j.core.backend.list.TermList;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.NavigableMap;

/**
 * Term list over the keys of a map that start with a prefix.
 *
 * @param <V> The value type
 * @since 0.1.0
 */
final class PrefixList<V> implements AllTermsList {

    /**
     * The statistics behind the keys.
     */
    private final Stats stats;

    /**
     * The prefix every key starts with.
     */
    private final String prefix;

    /**
     * The number of keys in the map when opened.
     */
    private final int count;

    /**
     * The cursor over the keys.
     */
    private final Seek<String, V> seek;

    /**
     * Creates a list over prefixed keys.
     *
     * @param map The map, ordered by term order
     * @param prefix The prefix every key starts with
     * @param stats The statistics behind the keys
     */
    PrefixList(
        final NavigableMap<String, V> map,
        final String prefix,
        final Stats stats
    ) {
        this(stats, prefix, map.size(), new Seek<>(map, prefix, item -> true));
    }

    /**
     * Creates a list from its parts.
     *
     * @param stats The statistics behind the keys
     * @param prefix The prefix every key starts with
     * @param count The number of keys in the map when opened
     * @param seek The cursor over the keys
     */
    private PrefixList(
        final Stats stats,
        final String prefix,
        final int count,
        final Seek<String, V> seek
    ) {
        this.stats = stats;
        this.prefix = prefix;
        this.count = count;
        this.seek = seek;
    }

    @Override
    public TermCount approxSize() {
        return new TermCount(this.count);
    }

    @Override
    public String term() {
        this.stats.live();
        return this.seek.current().getKey();
    }

    @Override
    public TermCount termFrequency() {
        this.stats.live();
        return this.stats.frequency(this.seek.current().getKey());
    }

    @Override
    public TermList next() {
        this.stats.live();
        this.seek.step();
        this.trim();
        return this;
    }

    @Override
    public TermList skipTo(final String target) {
        this.stats.live();
        this.seek.seek(target);
        this.trim();
        return this;
    }

    @Override
    public boolean atEnd() {
        return this.seek.ended();
    }

    private void trim() {
        if (!this.seek.ended()
            && !this.seek.current().getKey().startsWith(this.prefix)) {
            this.seek.finish();
        }
    }
}
