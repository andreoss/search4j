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
     * @return True unless the terms are exhausted
     */
    boolean hasNext();

    /**
     * Advances to the next term and returns it.
     *
     * @return Term above every one returned before
     */
    String next();

    /**
     * Skips the terms below a target, so the next term returned is the first
     * one at or after it.
     *
     * @param target Lowest term wanted
     */
    void skipTo(String target);

    /**
     * Within-document frequency of the current term.
     *
     * @return Frequency in the document listed
     */
    TermCount wdf();

    /**
     * Number of documents indexed by the current term.
     *
     * @return Document count in the database
     */
    TermCount termFrequency();

    /**
     * Number of positions of the current term.
     *
     * @return Position count, zero when unpositioned
     */
    TermCount positionCount();

    /**
     * Opens the positions of the current term.
     *
     * @return Positions in ascending order
     */
    PositionIterator positions();

    @Override
    void close();
}
