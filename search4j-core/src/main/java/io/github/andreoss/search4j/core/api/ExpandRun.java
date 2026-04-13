/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.TermCount;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * Selection of the best expansion terms offered in ascending term order:
 * terms weighing no more than the worst kept one are left out, so of equal
 * weights the earlier term is kept.
 *
 * @since 0.1.0
 */
final class ExpandRun {

    /**
     * Order of the terms, worst first: lower weight, then earlier term.
     */
    private static final Comparator<ESet.Item> WORST = Comparator
        .comparingDouble(ESet.Item::weight).thenComparing(ESet.Item::term);

    /**
     * Maximum number of terms kept.
     */
    private final int max;

    /**
     * Terms kept, worst first.
     */
    private final Queue<ESet.Item> kept;

    /**
     * Weight a term must exceed to be kept.
     */
    private double least;

    /**
     * Creates a selection.
     *
     * @param max Maximum number of terms kept
     */
    ExpandRun(final int max) {
        this.max = max;
        this.kept = new PriorityQueue<>(ExpandRun.WORST);
    }

    /**
     * Offers a term.
     *
     * @param term Term
     * @param freq Frequency of the term in the relevant documents
     * @param weight Expansion weight of the term
     */
    void offer(final String term, final TermCount freq, final double weight) {
        if (weight > this.least) {
            this.kept.add(new ESetItemImpl(term, freq, weight));
            if (this.kept.size() > this.max) {
                this.kept.poll();
            }
            if (this.kept.size() == this.max) {
                this.least = this.kept.peek().weight();
            }
        }
    }

    /**
     * Kept terms, best first.
     *
     * @return Terms with their weights
     */
    List<ESet.Item> items() {
        final List<ESet.Item> result = new ArrayList<>(this.kept);
        result.sort(ExpandRun.WORST.reversed());
        return result;
    }
}
