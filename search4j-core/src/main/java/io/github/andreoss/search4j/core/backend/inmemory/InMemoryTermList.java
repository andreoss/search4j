/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.TermList;
import io.github.andreoss.search4j.core.backend.list.VectorPositionList;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;

/**
 * Term list of one stored document.
 *
 * @since 0.1.0
 */
final class InMemoryTermList implements TermList {

    /**
     * The statistics behind the terms.
     */
    private final Stats stats;

    /**
     * The number of terms.
     */
    private final int count;

    /**
     * The cursor over the terms.
     */
    private final Seek<String, Occurrence> seek;

    /**
     * Creates a list over terms.
     *
     * @param terms The occurrences by term, ordered by term order
     * @param stats The statistics behind the terms
     */
    InMemoryTermList(
        final NavigableMap<String, Occurrence> terms,
        final Stats stats
    ) {
        this(stats, terms.size(), new Seek<>(terms, "", item -> true));
    }

    /**
     * Creates a list from its parts.
     *
     * @param stats The statistics behind the terms
     * @param count The number of terms
     * @param seek The cursor over the terms
     */
    private InMemoryTermList(
        final Stats stats,
        final int count,
        final Seek<String, Occurrence> seek
    ) {
        this.stats = stats;
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
    public TermList next() {
        this.stats.live();
        this.seek.step();
        return this;
    }

    @Override
    public TermList skipTo(final String target) {
        this.stats.live();
        this.seek.seek(target);
        return this;
    }

    @Override
    public boolean atEnd() {
        return this.seek.ended();
    }

    @Override
    public TermCount positionCount() {
        this.stats.live();
        return new TermCount(this.seek.current().getValue().positions().size());
    }

    @Override
    public PositionList positionList() {
        this.stats.live();
        return new VectorPositionList(this.seek.current().getValue().positions());
    }

    @Override
    public Optional<List<TermPos>> heldPositions() {
        this.stats.live();
        return Optional.of(this.seek.current().getValue().positions());
    }
}
