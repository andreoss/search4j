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

/**
 * Iterator over the documents posting a term, in ascending identifier order.
 *
 * <p>The accessors describe the posting most recently returned by
 * {@link #next()}.</p>
 *
 * @since 0.1.0
 */
public interface PostingIterator extends AutoCloseable {

    /**
     * Tests whether another posting is available.
     *
     * @return True when a posting is available
     */
    boolean hasNext();

    /**
     * Advances to the next posting and returns its document.
     *
     * @return The document identifier
     */
    DocId next();

    /**
     * Skips the postings below a target, so the next posting returned is the
     * first one at or after it.
     *
     * @param target The lowest document identifier wanted
     */
    void skipTo(DocId target);

    /**
     * Returns the within-document frequency of the current posting.
     *
     * @return The frequency
     */
    TermCount wdf();

    /**
     * Returns the length of the current document.
     *
     * @return The sum of within-document frequencies of its terms
     */
    TermCount docLength();

    /**
     * Returns the number of distinct terms in the current document.
     *
     * @return The distinct term count
     */
    TermCount uniqueTerms();

    /**
     * Returns the number of positions of the current posting.
     *
     * @return The position count
     */
    TermCount positionCount();

    /**
     * Opens the positions of the current posting.
     *
     * @return The position iterator
     */
    PositionIterator positions();

    @Override
    void close();
}
