/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.error.InvalidOperationError;
import java.util.NoSuchElementException;
import java.util.function.BooleanSupplier;

/**
 * Look-ahead state turning a move-then-read list into a has-next iterator.
 *
 * <p>The list is moved only when the iterator needs to know what comes next,
 * so a list opened over live contents sees changes made before that.</p>
 *
 * @since 0.1.0
 */
final class Stage {

    /**
     * Whether an element was handed out.
     */
    private boolean started;

    /**
     * Whether the list sits on an element not yet handed out.
     */
    private boolean staged;

    /**
     * Whether the iterator was closed.
     */
    private boolean closed;

    /**
     * Creates the state of a fresh iterator.
     */
    Stage() {
        this.started = false;
        this.staged = false;
        this.closed = false;
    }

    /**
     * Tests whether another element is available, moving the list if needed.
     *
     * @param step Move of the list to its next element
     * @param end Test of the list being exhausted
     * @return True when an element is available
     */
    boolean ready(final Runnable step, final BooleanSupplier end) {
        if (!this.closed && !this.staged) {
            step.run();
            this.staged = true;
        }
        return !this.closed && !end.getAsBoolean();
    }

    /**
     * Hands out the next element, leaving the list on it.
     *
     * @param step Move of the list to its next element
     * @param end Test of the list being exhausted
     */
    void take(final Runnable step, final BooleanSupplier end) {
        if (!this.ready(step, end)) {
            throw new NoSuchElementException("Iteration is over");
        }
        this.staged = false;
        this.started = true;
    }

    /**
     * Moves past every element below a target, never back over one handed
     * out.
     *
     * @param step Move of the list to its next element
     * @param end Test of the list being exhausted
     * @param skip Move of the list to the first element at or after the
     *  target
     */
    void skip(
        final Runnable step,
        final BooleanSupplier end,
        final Runnable skip
    ) {
        if (!this.closed) {
            if (this.started && !this.staged) {
                step.run();
            }
            if (!end.getAsBoolean()) {
                skip.run();
            }
            this.staged = true;
        }
    }

    /**
     * Ensures an element was handed out before it is described.
     */
    void shown() {
        if (!this.started) {
            throw new InvalidOperationError("Iteration has not started");
        }
    }

    /**
     * Ends the iteration.
     */
    void close() {
        this.closed = true;
    }
}
