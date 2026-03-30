/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;

/**
 * Value statistics and length bounds a shard provides.
 *
 * @since 0.1.0
 */
public interface ShardBounds {

    /**
     * Frequency and bounds of the values in a slot.
     *
     * @param slot Slot to describe
     * @return Statistics, {@link ValueStats#NONE} for an unused slot
     */
    ValueStats valueStats(ValueNo slot);

    /**
     * Lower bound on document length, ignoring empty documents.
     *
     * @return Length no non-empty document goes below
     */
    TermCount docLengthLowerBound();

    /**
     * Upper bound on document length.
     *
     * @return Length no document exceeds
     */
    TermCount docLengthUpperBound();

    /**
     * Upper bound on the within-document frequency of a term.
     *
     * @param term Non-empty term
     * @return Highest possible frequency
     */
    TermCount wdfUpperBound(String term);

    /**
     * Lower bound on the distinct terms of a non-empty document.
     *
     * @return One when any document may hold a term, else zero
     */
    default TermCount uniqueTermsLowerBound() {
        TermCount result = TermCount.ZERO;
        if (this.docLengthUpperBound().value() > 0) {
            result = new TermCount(1);
        }
        return result;
    }

    /**
     * Upper bound on the distinct terms of a document.
     *
     * @return Bound, the document length bound unless known tighter
     */
    default TermCount uniqueTermsUpperBound() {
        return this.docLengthUpperBound();
    }
}
