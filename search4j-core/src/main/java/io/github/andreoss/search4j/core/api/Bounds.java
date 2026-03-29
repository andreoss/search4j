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
     * Returns a lower bound on the length of any non-empty document.
     *
     * @return The lower bound
     */
    TermCount docLengthLowerBound();

    /**
     * Returns an upper bound on the length of any document.
     *
     * @return The upper bound
     */
    TermCount docLengthUpperBound();

    /**
     * Returns an upper bound on the within-document frequency of a term.
     *
     * @param term The term text
     * @return The upper bound, zero for the empty term
     */
    TermCount wdfUpperBound(String term);

    /**
     * Returns the number of documents with a value in a slot.
     *
     * @param slot The value slot number
     * @return The value frequency
     */
    TermCount valueFrequency(ValueNo slot);

    /**
     * Returns a lower bound on the values in a slot, empty when unused.
     *
     * @param slot The value slot number
     * @return The lower bound
     */
    String valueLowerBound(ValueNo slot);

    /**
     * Returns an upper bound on the values in a slot, empty when unused.
     *
     * @param slot The value slot number
     * @return The upper bound
     */
    String valueUpperBound(ValueNo slot);

    /**
     * Tests whether any document carries term positions.
     *
     * @return True when positions are stored
     */
    boolean hasPositions();
}
