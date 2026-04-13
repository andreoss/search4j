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
 * Orders positions of kept matches by the matches at them, better first.
 *
 * @since 0.5.4
 */
final class Ranks implements Comparator<Integer> {

    /**
     * Kept matches the positions point into.
     */
    private final List<Candidate> results;

    /**
     * Order of the matches, better first.
     */
    private final Comparator<Candidate> order;

    /**
     * Creates the order.
     *
     * @param results Kept matches the positions point into
     * @param order Order of the matches, better first
     */
    Ranks(final List<Candidate> results, final Comparator<Candidate> order) {
        this.results = results;
        this.order = order;
    }

    @Override
    public int compare(final Integer left, final Integer right) {
        return this.order.compare(this.results.get(left), this.results.get(right));
    }
}
