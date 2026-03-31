/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Within-document frequency and positions of a term in a document being
 * edited, kept with a deleted mark so a removed term can come back.
 *
 * @since 0.1.0
 */
final class TermEntry {

    /**
     * The positions in ascending order.
     */
    private final List<TermPos> positions;

    /**
     * The within-document frequency.
     */
    private int wdf;

    /**
     * Whether the term was removed.
     */
    private boolean deleted;

    /**
     * Creates an entry.
     *
     * @param wdf Within-document frequency
     * @param positions Ascending positions
     */
    TermEntry(final TermCount wdf, final List<TermPos> positions) {
        this(new ArrayList<>(positions), wdf.value());
    }

    /**
     * Creates a live entry over owned positions.
     *
     * @param positions Ascending positions, owned by the entry
     * @param wdf Within-document frequency
     */
    private TermEntry(final List<TermPos> positions, final int wdf) {
        this.positions = positions;
        this.wdf = wdf;
        this.deleted = false;
    }

    /**
     * Increases the frequency, reviving a removed term.
     *
     * @param delta Increment
     * @return True when the term was revived
     */
    boolean increase(final TermCount delta) {
        final boolean revived = this.deleted;
        if (revived) {
            this.wdf = delta.value();
            this.deleted = false;
        } else {
            this.wdf = new TermCount(this.wdf).plus(delta).value();
        }
        return revived;
    }

    /**
     * Decreases the frequency, never below zero, removing the term once it
     * has neither frequency nor positions.
     *
     * @param delta Decrement
     * @return True when the term was removed
     */
    boolean decrease(final TermCount delta) {
        this.wdf = Math.max(0, this.wdf - delta.value());
        this.deleted = this.wdf == 0 && this.positions.isEmpty();
        return this.deleted;
    }

    /**
     * Removes the term with its positions.
     */
    void remove() {
        this.positions.clear();
        this.deleted = true;
    }

    /**
     * Adds a position and increases the frequency, reviving a removed term.
     *
     * @param delta Increment
     * @param position Position, kept once when repeated
     * @return True when the term was revived
     */
    boolean place(final TermCount delta, final TermPos position) {
        final boolean revived = this.increase(delta);
        final int found = Collections.binarySearch(this.positions, position);
        if (found < 0) {
            this.positions.add(-found - 1, position);
        }
        return revived;
    }

    /**
     * Removes a position, leaving the frequency alone.
     *
     * @param position Position removed
     * @return True when the position was present
     */
    boolean unplace(final TermPos position) {
        return this.positions.remove(position);
    }

    /**
     * Tests whether the term was removed.
     *
     * @return True when removed
     */
    boolean deleted() {
        return this.deleted;
    }

    /**
     * Tests whether the term has positions.
     *
     * @return True when positioned
     */
    boolean positioned() {
        return !this.positions.isEmpty();
    }

    /**
     * The within-document frequency.
     *
     * @return Within-document frequency
     */
    TermCount wdf() {
        return new TermCount(this.wdf);
    }

    /**
     * The positions.
     *
     * @return Ascending positions, an unmodifiable copy
     */
    List<TermPos> positions() {
        return List.copyOf(this.positions);
    }

    /**
     * Copies the entry.
     *
     * @return Independent entry, removed when this one is
     */
    TermEntry copy() {
        final TermEntry result = new TermEntry(this.wdf(), this.positions);
        result.deleted = this.deleted;
        return result;
    }
}
