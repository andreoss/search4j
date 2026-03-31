/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

/**
 * Transactions of a writable database: the changes between the beginning
 * and the end of a transaction are committed or dropped together.
 *
 * <p>A backend without transactions raises an unimplemented error.</p>
 *
 * @since 0.1.0
 */
public interface Transactional {

    /**
     * Opens a transaction.
     *
     * <p>A flushed transaction commits the changes pending before it.</p>
     *
     * @param flushed True to commit pending changes first
     */
    void beginTransaction(boolean flushed);

    /**
     * Commits the open transaction; an unflushed one stays pending.
     */
    void commitTransaction();

    /**
     * Drops the changes of the open transaction.
     */
    void cancelTransaction();
}
