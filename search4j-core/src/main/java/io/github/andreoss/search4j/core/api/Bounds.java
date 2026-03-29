/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;

/**
 * Bounds and flags of a database that let a search prune its work.
 *
 * @since 0.1.0
 */
public interface Bounds {

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
     * @param term Term to bound
     * @return Highest possible frequency, zero for the empty term
     */
    TermCount wdfUpperBound(String term);

    /**
     * Number of documents holding a value in a slot.
     *
     * @param slot Slot to count
     * @return Document count, zero for an unused slot
     */
    TermCount valueFrequency(ValueNo slot);

    /**
     * Lower bound on the values in a slot.
     *
     * @param slot Slot to bound
     * @return Bound, empty when the slot is unused
     */
    String valueLowerBound(ValueNo slot);

    /**
     * Upper bound on the values in a slot.
     *
     * @param slot Slot to bound
     * @return Bound, empty when the slot is unused
     */
    String valueUpperBound(ValueNo slot);

    /**
     * Tests whether any document carries term positions.
     *
     * @return True when at least one term has a position
     */
    boolean hasPositions();
}
