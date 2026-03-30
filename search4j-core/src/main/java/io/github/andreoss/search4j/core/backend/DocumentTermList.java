/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.TermList;
import io.github.andreoss.search4j.core.backend.list.VectorPositionList;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;

/**
 * Term list over the edited terms of a document, skipping removed ones.
 *
 * @since 0.1.0
 */
final class DocumentTermList implements TermList {

    /**
     * The entries by term, ordered by {@link TermOrder}.
     */
    private final NavigableMap<String, TermEntry> terms;

    /**
     * The current entry, absent before the start and at the end.
     */
    private Optional<Map.Entry<String, TermEntry>> current;

    /**
     * Whether a move was made.
     */
    private boolean started;

    /**
     * Creates a list over entries.
     *
     * @param terms The entries by term, ordered by {@link TermOrder}
     */
    DocumentTermList(final NavigableMap<String, TermEntry> terms) {
        this(terms, Optional.empty());
    }

    /**
     * Creates a list on an entry.
     *
     * @param terms The entries
     * @param current The current entry, absent before the start
     */
    private DocumentTermList(
        final NavigableMap<String, TermEntry> terms,
        final Optional<Map.Entry<String, TermEntry>> current
    ) {
        this.terms = terms;
        this.current = current;
        this.started = false;
    }

    @Override
    public TermCount approxSize() {
        return new TermCount(
            (int) this.terms.values().stream()
                .filter(entry -> !entry.deleted()).count()
        );
    }

    @Override
    public String term() {
        return this.entry().getKey();
    }

    @Override
    public TermCount wdf() {
        return this.entry().getValue().wdf();
    }

    @Override
    public TermCount termFrequency() {
        throw new InvalidOperationError(
            "Term frequency needs a document held by a database"
        );
    }

    @Override
    public TermList next() {
        if (this.started) {
            this.current.ifPresent(
                entry -> this.land(this.terms.higherEntry(entry.getKey()))
            );
        } else {
            this.started = true;
            this.land(this.terms.firstEntry());
        }
        return this;
    }

    @Override
    public TermList skipTo(final String target) {
        if (!this.started || this.current.filter(
            entry -> TermOrder.INSTANCE.compare(target, entry.getKey()) > 0
        ).isPresent()) {
            this.started = true;
            this.land(this.terms.ceilingEntry(target));
        }
        return this;
    }

    @Override
    public boolean atEnd() {
        return this.started && this.current.isEmpty();
    }

    @Override
    public TermCount positionCount() {
        return new TermCount(this.entry().getValue().positions().size());
    }

    @Override
    public PositionList positionList() {
        return new VectorPositionList(this.entry().getValue().positions());
    }

    @Override
    public Optional<List<TermPos>> heldPositions() {
        return Optional.of(this.entry().getValue().positions());
    }

    private Map.Entry<String, TermEntry> entry() {
        return this.current.orElseThrow(
            () -> new InvalidOperationError("Term list is not on a term")
        );
    }

    private void land(final Map.Entry<String, TermEntry> from) {
        Map.Entry<String, TermEntry> entry = from;
        while (entry != null && entry.getValue().deleted()) {
            entry = this.terms.higherEntry(entry.getKey());
        }
        this.current = Optional.ofNullable(entry);
    }
}
