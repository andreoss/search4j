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
 * Iterator over the terms of a document or database.
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
     * Returns the current term and advances.
     *
     * @return The term text
     */
    String next();

    /**
     * Returns the current term frequency.
     *
     * @return The frequency
     */
    TermCount frequency();

    @Override
    void close();
}
