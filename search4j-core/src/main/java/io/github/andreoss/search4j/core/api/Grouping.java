/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import java.util.Comparator;
import java.util.function.IntFunction;

/**
 * Settings shared by the collapse groups of a run.
 *
 * @param max Maximum number of matches kept per key
 * @param slots Kept matches by position, including positions dropped since
 * @param order Order of the matches, better first
 * @since 0.5.4
 */
record Grouping(int max, IntFunction<Candidate> slots, Comparator<Candidate> order) {

    /**
     * Order of positions by the matches at them, better first.
     *
     * @return Comparator of positions
     */
    Comparator<Integer> ranks() {
        return (left, right) -> this.order.compare(
            this.slots.apply(left), this.slots.apply(right)
        );
    }
}
