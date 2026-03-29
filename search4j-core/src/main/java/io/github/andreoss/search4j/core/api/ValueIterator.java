/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.ValueNo;

/**
 * Iterator over the value slots of a document.
 *
 * @since 0.1.0
 */
public interface ValueIterator extends AutoCloseable {

    /**
     * Tests whether another value is available.
     *
     * @return True when a value is available
     */
    boolean hasNext();

    /**
     * Returns the current value and advances.
     *
     * @return The value text
     */
    String next();

    /**
     * Returns the slot number of the current value.
     *
     * @return The slot number
     */
    ValueNo slot();

    @Override
    void close();
}
