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
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.NavigableMap;

/**
 * Iterator over the terms of one document.
 *
 * @since 0.1.0
 */
final class TermCursor implements TermIterator {

    /**
     * The statistics behind the terms.
     */
    private final Stats stats;

    /**
     * The cursor over the terms.
     */
    private final Seek<String, Occurrence> seek;

    /**
     * Creates an iterator over terms.
     *
     * @param terms The occurrences by term, ordered by {@link TermOrder}
     * @param stats The statistics behind the terms
     */
    TermCursor(
        final NavigableMap<String, Occurrence> terms,
        final Stats stats
    ) {
        this.stats = stats;
        this.seek = new Seek<>(terms, "", item -> true);
    }

    @Override
    public boolean hasNext() {
        return this.stats.live() && this.seek.peek().isPresent();
    }

    @Override
    public String next() {
        this.stats.live();
        return this.seek.advance().getKey();
    }

    @Override
    public void skipTo(final String target) {
        this.stats.live();
        this.seek.skipTo(target);
    }

    @Override
    public TermCount wdf() {
        this.stats.live();
        return this.seek.current().getValue().wdf();
    }

    @Override
    public TermCount termFrequency() {
        this.stats.live();
        return this.stats.frequency(this.seek.current().getKey());
    }

    @Override
    public TermCount positionCount() {
        this.stats.live();
        return new TermCount(
            this.seek.current().getValue().positions().size()
        );
    }

    @Override
    public PositionIterator positions() {
        this.stats.live();
        return new PositionCursor(this.seek.current().getValue().positions());
    }

    @Override
    public void close() {
        this.seek.finish();
    }
}
