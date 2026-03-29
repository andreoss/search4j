/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Mutable frequency and positions of a term in a document being built.
 *
 * @since 0.1.0
 */
final class Tally {

    /**
     * The positions in ascending order.
     */
    private final NavigableSet<TermPos> positions;

    /**
     * The within-document frequency.
     */
    private TermCount wdf;

    /**
     * Creates a tally with no frequency and no positions.
     */
    Tally() {
        this.positions = new TreeSet<>();
        this.wdf = TermCount.ZERO;
    }

    /**
     * Increases the frequency.
     *
     * @param increment The frequency increment
     */
    void increase(final TermCount increment) {
        this.wdf = this.wdf.plus(increment);
    }

    /**
     * Adds a position, keeping one copy of a repeated position.
     *
     * @param position The term position
     */
    void place(final TermPos position) {
        this.positions.add(position);
    }

    /**
     * Removes a position and decreases the frequency, never below zero.
     *
     * @param position The term position
     * @param decrement The frequency decrement
     * @return True when the position was present
     */
    boolean lift(final TermPos position, final TermCount decrement) {
        final boolean found = this.positions.remove(position);
        if (found) {
            this.wdf = new TermCount(
                Math.max(0, this.wdf.value() - decrement.value())
            );
        }
        return found;
    }

    /**
     * Tests whether neither frequency nor positions remain.
     *
     * @return True when spent
     */
    boolean spent() {
        return this.wdf.value() == 0 && this.positions.isEmpty();
    }

    /**
     * Freezes the tally into an immutable occurrence.
     *
     * @return Occurrence
     */
    Occurrence freeze() {
        return new Occurrence(this.wdf, List.copyOf(this.positions));
    }
}
