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
     * @return True unless the postings are exhausted
     */
    boolean hasNext();

    /**
     * Advances to the next posting and returns its document.
     *
     * @return Identifier above every one returned before
     */
    DocId next();

    /**
     * Skips the postings below a target, so the next posting returned is the
     * first one at or after it.
     *
     * @param target Lowest document identifier wanted
     */
    void skipTo(DocId target);

    /**
     * Within-document frequency of the current posting.
     *
     * @return Frequency, one for every posting of the empty term
     */
    TermCount wdf();

    /**
     * Length of the current document.
     *
     * @return Sum of the within-document frequencies of its terms
     */
    TermCount docLength();

    /**
     * Number of distinct terms in the current document.
     *
     * @return Distinct term count, at most the document length
     */
    TermCount uniqueTerms();

    /**
     * Number of positions of the current posting.
     *
     * @return Position count, zero when unpositioned
     */
    TermCount positionCount();

    /**
     * Opens the positions of the current posting.
     *
     * @return Positions in ascending order
     */
    PositionIterator positions();

    @Override
    void close();
}
