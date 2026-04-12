/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import java.util.Comparator;
import java.util.List;

/**
 * Orders indices of kept matches so that the worst match comes first.
 *
 * @since 0.4.0
 */
final class WorstFirst implements Comparator<Integer> {

    /**
     * Kept matches the indices point into.
     */
    private final List<Candidate> results;

    /**
     * Order of the matches, better first.
     */
    private final Comparator<Candidate> order;

    /**
     * Creates the order.
     *
     * @param results Kept matches the indices point into
     * @param order Order of the matches, better first
     */
    WorstFirst(final List<Candidate> results, final Comparator<Candidate> order) {
        this.results = results;
        this.order = order;
    }

    @Override
    public int compare(final Integer left, final Integer right) {
        return this.order.compare(this.results.get(right), this.results.get(left));
    }
}
