/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.ArrayList;
import java.util.List;

/**
 * Term list over a fixed list of terms held in memory.
 *
 * <p>Each term has within-document frequency one and no positions, which is
 * what a document built from a word list needs.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.UncommentedEmptyMethodBody")
public final class VectorTermList implements TermIterator {

    /**
     * Terms in ascending order.
     */
    private final List<String> terms;

    /**
     * Index of the current term, minus one before the start.
     */
    private int index;

    /**
     * Creates a term list over a copy of terms.
     *
     * @param terms Terms in ascending order
     */
    public VectorTermList(final List<String> terms) {
        this.terms = new ArrayList<>(terms);
        this.index = -1;
    }

    @Override
    public boolean hasNext() {
        return this.index + 1 < this.terms.size();
    }

    @Override
    public String next() {
        if (!this.hasNext()) {
            throw new InvalidOperationError("No term left to read");
        }
        this.index = this.index + 1;
        return this.terms.get(this.index);
    }

    @Override
    public void skipTo(final String target) {
        while (this.hasNext() && this.terms.get(this.index + 1)
            .compareTo(target) < 0) {
            this.index = this.index + 1;
        }
    }

    @Override
    public TermCount wdf() {
        this.current();
        return TermCount.ONE;
    }

    @Override
    public TermCount termFrequency() {
        this.current();
        return TermCount.ZERO;
    }

    @Override
    public TermCount positionCount() {
        this.current();
        return TermCount.ZERO;
    }

    @Override
    public PositionIterator positions() {
        throw new InvalidOperationError(
            "A vector term list has no positions"
        );
    }

    @Override
    public void close() {
    }

    private void current() {
        if (this.index < 0 || this.index >= this.terms.size()) {
            throw new InvalidOperationError("No term is current");
        }
    }
}
