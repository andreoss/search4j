/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.TermPos;

/**
 * Iterator over the positions of a term within a document, in ascending
 * order.
 *
 * @since 0.1.0
 */
public interface PositionIterator extends AutoCloseable {

    /**
     * Tests whether another position is available.
     *
     * @return True when a position is available
     */
    boolean hasNext();

    /**
     * Returns the next position and advances.
     *
     * @return The term position
     */
    TermPos next();

    /**
     * Skips the positions below a target, so the next position returned is
     * the first one at or after it.
     *
     * @param target The lowest position wanted
     */
    void skipTo(TermPos target);

    @Override
    void close();
}
