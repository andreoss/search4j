/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.TermCount;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * An expansion set: a list of terms for query expansion with their weights.
 *
 * <p>An ESet is produced by the {@link Enquire#getESet(int)} method and
 * contains the most informative terms from the top matches of a query,
 * weighted by their relevance.</p>
 *
 * @since 0.1.0
 */
public interface ESet extends Iterable<ESet.Item> {

    /**
     * Number of terms in the expansion set.
     *
     * @return Term count
     */
    TermCount size();

    /**
     * Returns the first item or throws when empty.
     *
     * @return First item
     * @throws NoSuchElementException when no items
     */
    Item first() throws NoSuchElementException;

    /**
     * Returns the item at an index.
     *
     * @param index Zero-based index below {@link #size()}
     * @return Item at the index
     * @throws IndexOutOfBoundsException when out of range
     */
    Item get(int index) throws IndexOutOfBoundsException;

    /**
     * Returns all items as a list.
     *
     * @return List of items
     */
    List<Item> toList();

    /**
     * An item in an ESet: a term with its expansion weight.
     *
     * @since 0.1.0
     */
    interface Item {

        /**
         * The term text.
         *
         * @return Term
         */
        String term();

        /**
         * Estimated number of documents containing the term.
         *
         * @return Document frequency estimate
         */
        TermCount estimatedTermFrequency();

        /**
         * Weight of the term for expansion.
         *
         * @return Expansion weight, non-negative
         */
        double weight();
    }
}
