/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.List;

/**
 * A match set: an ordered list of matching documents with their weights.
 *
 * <p>An MSet is a view over the results of a search; it is not a snapshot
 * and may reflect database changes made after the search unless the
 * database is wrapped with {@link Database#addDatabase(Database)}.</p>
 *
 * @since 0.1.0
 */
public interface MSet extends Iterable<MSet.Item> {

    /**
     * The number of items in this match set.
     *
     * @return Number of items, zero when empty
     */
    TermCount size();

    /**
     * The number of items in this match set, for container compatibility.
     *
     * @return Same as {@link #size()}
     */
    TermCount maxSize();

    /**
     * A lower bound on the number of matching documents.
     *
     * @return Lower bound, zero when empty
     */
    TermCount matchesLowerBound();

    /**
     * An estimate of the number of matching documents.
     *
     * @return Estimate between the bounds, zero when empty
     */
    TermCount matchesEstimated();

    /**
     * An upper bound on the number of matching documents.
     *
     * @return Upper bound, zero when empty
     */
    TermCount matchesUpperBound();

    /**
     * Returns the first item or throws when empty.
     *
     * @return First item
     */
    Item first();

    /**
     * Returns the item at an index.
     *
     * @param index Zero-based index below {@link #size()}
     * @return Item at the index
     */
    Item get(int index);

    /**
     * Returns all items as a list.
     *
     * @return List of items
     */
    List<Item> toList();

    /**
     * An item in an MSet: a matching document with its weight.
     *
     * @since 0.1.0
     */
    interface Item {

        /**
         * Document identifier of the match.
         *
         * @return Document identifier
         */
        DocId docId();

        /**
         * Weight of the match.
         *
         * @return Weight, non-negative
         */
        double weight();

        /**
         * Rank of the match in the ordered MSet (one-based).
         *
         * @return Rank, one for the top match
         */
        int rank();

        /**
         * Document of the match.
         *
         * @return Document, throws when the document is missing
         */
        Document document();
    }
}
