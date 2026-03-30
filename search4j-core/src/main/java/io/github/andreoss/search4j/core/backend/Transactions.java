/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.error.UnimplementedError;
import java.util.function.Supplier;

/**
 * Transaction state machine of one shard.
 *
 * <p>Beginning and ending a transaction is legal only from the matching
 * state; a read-only shard and a shard without transactions reject both.</p>
 *
 * @since 0.1.0
 */
public final class Transactions {

    /**
     * The current state.
     */
    private TransactionState current;

    /**
     * Creates the state machine.
     *
     * @param initial State the shard starts in
     */
    public Transactions(final TransactionState initial) {
        this.current = initial;
    }

    /**
     * Current state.
     *
     * @return State
     */
    public TransactionState state() {
        return this.current;
    }

    /**
     * Opens a transaction.
     *
     * @param flushed True to commit pending changes first
     * @param commit Commit of the shard, run when flushed
     */
    public void begin(final boolean flushed, final Runnable commit) {
        if (this.current != TransactionState.NONE) {
            if (this.current.active()) {
                throw new InvalidOperationError("Already in a transaction");
            }
            this.unsupported("Beginning a transaction");
        }
        if (flushed) {
            commit.run();
            this.current = TransactionState.FLUSHED;
        } else {
            this.current = TransactionState.UNFLUSHED;
        }
    }

    /**
     * Closes the open transaction, keeping or dropping its changes.
     *
     * @param keep True to commit, false to cancel
     * @param commit Commit of the shard
     * @param cancel Cancel of the shard
     */
    public void end(
        final boolean keep,
        final Runnable commit,
        final Runnable cancel
    ) {
        if (!this.current.active()) {
            if (this.current != TransactionState.NONE) {
                this.unsupported("Ending a transaction");
            }
            throw new InvalidOperationError("Not in a transaction");
        }
        final TransactionState old = this.current;
        this.current = TransactionState.NONE;
        if (keep) {
            if (old == TransactionState.FLUSHED) {
                commit.run();
            }
        } else {
            cancel.run();
        }
    }

    /**
     * Runs a group of changes as if inside a transaction, so they are not
     * committed one by one, restoring the state afterwards.
     *
     * @param work Changes to run
     * @param <T> Result type
     * @return Result of the changes
     */
    public <T> T batch(final Supplier<T> work) {
        final TransactionState old = this.current;
        this.hold();
        try {
            return work.get();
        } finally {
            this.current = old;
        }
    }

    private void hold() {
        if (this.current != TransactionState.UNIMPLEMENTED) {
            this.current = TransactionState.UNFLUSHED;
        }
    }

    private void unsupported(final String action) {
        if (this.current == TransactionState.READ_ONLY) {
            throw new InvalidOperationError(
                String.format("%s needs a writable shard", action)
            );
        }
        throw new UnimplementedError(
            "This backend does not implement transactions"
        );
    }
}
