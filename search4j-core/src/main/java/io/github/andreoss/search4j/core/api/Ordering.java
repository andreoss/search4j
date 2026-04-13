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
 * Primary order of a match set; ties are broken by document identifier in
 * the chosen direction.
 *
 * @since 0.4.0
 */
enum Ordering {

    /**
     * Document identifier only, used when no weight can be positive.
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
     * @param forward Whether document identifiers ascend
     * @return Comparator, negative when the first candidate ranks higher
     */
    Comparator<Candidate> comparator(final boolean ascending, final boolean forward) {
        final Comparator<Candidate> docids = Ordering.docids(forward);
        final Comparator<Candidate> result;
        if (this == Ordering.DOCID) {
            result = docids;
        } else if (this == Ordering.RELEVANCE) {
            result = (left, right) -> Ordering.heavier(left, right, docids);
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
     * Whether the collection may end once the set is full and enough matches
     * were counted, given ascending document identifiers.
     *
     * @return True for document order
     */
    boolean stops() {
        return this == Ordering.DOCID;
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

    private static Comparator<Candidate> docids(final boolean forward) {
        final Comparator<Candidate> ascending = Comparator.comparing(Candidate::docId);
        Comparator<Candidate> result = ascending;
        if (!forward) {
            result = ascending.reversed();
        }
        return result;
    }

    private static int heavier(
        final Candidate left, final Candidate right, final Comparator<Candidate> ties
    ) {
        int result = ties.compare(left, right);
        if (left.weight() > right.weight()) {
            result = -1;
        } else if (left.weight() < right.weight()) {
            result = 1;
        }
        return result;
    }
}
