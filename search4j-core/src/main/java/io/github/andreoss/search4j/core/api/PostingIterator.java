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
 * Iterator over the documents posting a term.
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
     * Returns the current posting document and advances.
     *
     * @return The document identifier
     */
    DocId next();

    /**
     * Returns the within-document frequency of the current posting.
     *
     * @return The frequency
     */
    TermCount frequency();

    /**
     * Returns the number of positions of the current posting.
     *
     * @return The position count
     */
    TermCount positionCount();

    @Override
    void close();
}
