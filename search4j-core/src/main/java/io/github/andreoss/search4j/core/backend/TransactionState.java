/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

/**
 * Transaction state of a shard.
 *
 * @since 0.1.0
 */
public enum TransactionState {

    /**
     * The shard cannot be written.
     */
    READ_ONLY,

    /**
     * The shard is writable but has no transactions.
     */
    UNIMPLEMENTED,

    /**
     * No transaction is open.
     */
    NONE,

    /**
     * A transaction is open and changes before it were not committed.
     */
    UNFLUSHED,

    /**
     * A transaction is open and changes before it were committed.
     */
    FLUSHED;

    /**
     * Tests whether a transaction is open.
     *
     * @return True in either open state
     */
    public boolean active() {
        return this == UNFLUSHED || this == FLUSHED;
    }
}
