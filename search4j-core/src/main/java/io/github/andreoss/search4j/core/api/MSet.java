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
public interface MSet extends MatchCounts, Iterable<MSet.Item> {

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

        /**
         * Collapse key of the match.
         *
         * @return Collapse key, empty when not collapsing or when the
         *  document has no key
         * @since 0.7.0
         */
        String collapseKey();

        /**
         * Number of matches with the same collapse key left out in favour of
         * this one or the other kept matches with the key.
         *
         * <p>A positive count means at least that many other documents have
         * the key; zero means there may or may not be others. Under a
         * percentage cutoff the count is one when a left-out match reached
         * the minimum weight, otherwise zero.</p>
         *
         * @return Collapse count, zero when not collapsing or when the match
         *  has no key
         * @since 0.7.0
         */
        TermCount collapseCount();
    }
}
