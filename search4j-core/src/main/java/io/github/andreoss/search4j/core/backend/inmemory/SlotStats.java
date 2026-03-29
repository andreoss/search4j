/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Frequency and bounds of the values held in one slot.
 *
 * <p>Bounds widen as values arrive and reset only once the slot is unused,
 * so they may be looser than the values present.</p>
 *
 * @since 0.1.0
 */
final class SlotStats {

    /**
     * The number of documents holding the slot.
     */
    private int count;

    /**
     * The lower bound.
     */
    private String lower;

    /**
     * The upper bound.
     */
    private String upper;

    /**
     * Creates statistics of an unused slot.
     */
    SlotStats() {
        this.count = 0;
        this.lower = "";
        this.upper = "";
    }

    /**
     * Accounts for a value arriving.
     *
     * @param value The value text
     */
    void add(final String value) {
        if (this.count == 0) {
            this.lower = value;
            this.upper = value;
        } else {
            if (TermOrder.INSTANCE.compare(value, this.lower) < 0) {
                this.lower = value;
            }
            if (TermOrder.INSTANCE.compare(value, this.upper) > 0) {
                this.upper = value;
            }
        }
        this.count += 1;
    }

    /**
     * Accounts for a value leaving.
     */
    void remove() {
        this.count -= 1;
        if (this.count == 0) {
            this.lower = "";
            this.upper = "";
        }
    }

    /**
     * Returns the number of documents holding the slot.
     *
     * @return The value frequency
     */
    TermCount frequency() {
        return new TermCount(this.count);
    }

    /**
     * Returns the lower bound.
     *
     * @return The lower bound, empty when unused
     */
    String lower() {
        return this.lower;
    }

    /**
     * Returns the upper bound.
     *
     * @return The upper bound, empty when unused
     */
    String upper() {
        return this.upper;
    }
}
