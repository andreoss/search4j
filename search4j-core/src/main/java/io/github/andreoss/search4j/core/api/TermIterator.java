/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Iterator over terms in ascending byte order, from a document or a database.
 *
 * <p>The accessors describe the term most recently returned by
 * {@link #next()}. An accessor that has no meaning for the source raises an
 * invalid-operation error.</p>
 *
 * @since 0.1.0
 */
public interface TermIterator extends AutoCloseable {

    /**
     * Tests whether another term is available.
     *
     * @return True when a term is available
     */
    boolean hasNext();

    /**
     * Advances to the next term and returns it.
     *
     * @return The term text
     */
    String next();

    /**
     * Skips the terms below a target, so the next term returned is the first
     * one at or after it.
     *
     * @param target The lowest term wanted
     */
    void skipTo(String target);

    /**
     * Returns the within-document frequency of the current term.
     *
     * @return The frequency
     */
    TermCount wdf();

    /**
     * Returns the number of documents indexed by the current term.
     *
     * @return The term frequency
     */
    TermCount termFrequency();

    /**
     * Returns the number of positions of the current term.
     *
     * @return The position count
     */
    TermCount positionCount();

    /**
     * Opens the positions of the current term.
     *
     * @return The position iterator
     */
    PositionIterator positions();

    @Override
    void close();
}
