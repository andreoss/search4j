/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.error.UnimplementedError;
import java.util.Comparator;

/**
 * Primary order of a match set; ties are broken by ascending document
 * identifier.
 *
 * @since 0.4.0
 */
enum Ordering {

    /**
     * Ascending document identifier only, used when no weight can be
     * positive.
     */
    DOCID,

    /**
     * Decreasing weight.
     */
    RELEVANCE,

    /**
     * Sort key.
     */
    VALUE;

    /**
     * Comparator placing better candidates first.
     *
     * @param ascending Whether sort keys ascend, used by value order only
     * @return Comparator, negative when the first candidate ranks higher
     */
    Comparator<Candidate> comparator(final boolean ascending) {
        final Comparator<Candidate> docids = Comparator.comparing(Candidate::docId);
        final Comparator<Candidate> result;
        if (this == Ordering.DOCID) {
            result = docids;
        } else if (this == Ordering.RELEVANCE) {
            result = Ordering::heavier;
        } else if (ascending) {
            result = Comparator.<Candidate, String>comparing(Candidate::key).thenComparing(docids);
        } else {
            result = Comparator.<Candidate, String>comparing(Candidate::key).reversed()
                .thenComparing(docids);
        }
        return result;
    }

    /**
     * Whether weight is the primary key, so the lowest kept weight becomes
     * the minimum weight once the set is full.
     *
     * @return True for relevance order
     */
    boolean weightFirst() {
        return this == Ordering.RELEVANCE;
    }

    /**
     * Refuses a percentage cutoff when sorting primarily by value.
     *
     * @param percent Percentage cutoff, zero for none
     */
    void refuse(final int percent) {
        if (percent != 0 && this == Ordering.VALUE) {
            throw new UnimplementedError(
                "A percentage cutoff while sorting by value is not supported"
            );
        }
    }

    private static int heavier(final Candidate left, final Candidate right) {
        int result = left.docId().compareTo(right.docId());
        if (left.weight() > right.weight()) {
            result = -1;
        } else if (left.weight() < right.weight()) {
            result = 1;
        }
        return result;
    }
}
