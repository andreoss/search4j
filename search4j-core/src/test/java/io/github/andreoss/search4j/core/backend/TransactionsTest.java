/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the backend contract and support types.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.error.UnimplementedError;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link Transactions} and {@link TransactionState}.
 *
 * @since 0.1.0
 */
final class TransactionsTest {

    @Test
    void beginsUnflushedWithoutCommit() {
        final List<String> calls = new ArrayList<>(0);
        final Transactions states = new Transactions(TransactionState.NONE);
        states.begin(false, () -> calls.add("commit"));
        calls.add(states.state().name());
        Assertions.assertEquals(List.of("UNFLUSHED"), calls, "Unflushed");
    }

    @Test
    void beginsFlushedAfterCommit() {
        final List<String> calls = new ArrayList<>(0);
        final Transactions states = new Transactions(TransactionState.NONE);
        states.begin(true, () -> calls.add("commit"));
        calls.add(states.state().name());
        Assertions.assertEquals(
            List.of("commit", "FLUSHED"), calls, "Committed first"
        );
    }

    @Test
    void rejectsNestedBegin() {
        final Transactions states = new Transactions(TransactionState.NONE);
        states.begin(false, () -> { });
        Assertions.assertThrows(
            InvalidOperationError.class,
            () -> states.begin(false, () -> { }),
            "Nested unflushed"
        );
    }

    @Test
    void rejectsBeginInsideFlushed() {
        final Transactions states = new Transactions(TransactionState.FLUSHED);
        Assertions.assertThrows(
            InvalidOperationError.class,
            () -> states.begin(true, () -> { }),
            "Nested flushed"
        );
    }

    @Test
    void rejectsBeginOnReadOnly() {
        final Transactions states =
            new Transactions(TransactionState.READ_ONLY);
        Assertions.assertThrows(
            InvalidOperationError.class,
            () -> states.begin(false, () -> { }),
            "Read-only"
        );
    }

    @Test
    void rejectsBeginWithoutSupport() {
        final Transactions states =
            new Transactions(TransactionState.UNIMPLEMENTED);
        Assertions.assertThrows(
            UnimplementedError.class,
            () -> states.begin(false, () -> { }),
            "Unimplemented"
        );
    }

    @Test
    void commitsFlushedOnEnd() {
        Assertions.assertEquals(
            List.of("commit", "NONE"),
            TransactionsTest.ended(TransactionState.FLUSHED, true),
            "Committed"
        );
    }

    @Test
    void leavesUnflushedUncommittedOnEnd() {
        Assertions.assertEquals(
            List.of("NONE"),
            TransactionsTest.ended(TransactionState.UNFLUSHED, true),
            "Left pending"
        );
    }

    @Test
    void cancelsOnEnd() {
        Assertions.assertEquals(
            List.of("cancel", "NONE"),
            TransactionsTest.ended(TransactionState.FLUSHED, false),
            "Cancelled"
        );
    }

    @Test
    void rejectsEndOutsideTransaction() {
        final Transactions states = new Transactions(TransactionState.NONE);
        Assertions.assertThrows(
            InvalidOperationError.class,
            () -> states.end(true, () -> { }, () -> { }),
            "Not in a transaction"
        );
    }

    @Test
    void rejectsEndOnReadOnly() {
        final Transactions states =
            new Transactions(TransactionState.READ_ONLY);
        String message = "";
        try {
            states.end(true, () -> { }, () -> { });
        } catch (final InvalidOperationError ex) {
            message = ex.getMessage();
        }
        Assertions.assertEquals(
            "Ending a transaction needs a writable shard", message, "Cause"
        );
    }

    @Test
    void rejectsEndWithoutSupport() {
        final Transactions states =
            new Transactions(TransactionState.UNIMPLEMENTED);
        Assertions.assertThrows(
            UnimplementedError.class,
            () -> states.end(false, () -> { }, () -> { }),
            "Unimplemented"
        );
    }

    @Test
    void holdsChangesInBatch() {
        final Transactions states = new Transactions(TransactionState.NONE);
        Assertions.assertEquals(
            List.of(TransactionState.UNFLUSHED, TransactionState.NONE),
            List.of(states.batch(states::state), states.state()),
            "Held during, restored after"
        );
    }

    @Test
    void keepsUnsupportedStateInBatch() {
        final Transactions states =
            new Transactions(TransactionState.UNIMPLEMENTED);
        Assertions.assertEquals(
            TransactionState.UNIMPLEMENTED,
            states.batch(states::state),
            "Unchanged during"
        );
    }

    @Test
    void restoresStateAfterFailedBatch() {
        final Transactions states = new Transactions(TransactionState.FLUSHED);
        try {
            states.batch(
                () -> {
                    throw new IllegalStateException("failed");
                }
            );
        } catch (final IllegalStateException ex) {
            states.state();
        }
        Assertions.assertEquals(
            TransactionState.FLUSHED, states.state(), "Restored"
        );
    }

    @Test
    void marksOpenStatesActive() {
        final List<Boolean> active = new ArrayList<>(0);
        for (final TransactionState state : TransactionState.values()) {
            active.add(state.active());
        }
        Assertions.assertEquals(
            List.of(false, false, false, true, true), active, "Open states"
        );
    }

    private static List<String> ended(
        final TransactionState initial,
        final boolean keep
    ) {
        final List<String> calls = new ArrayList<>(0);
        final Transactions states = new Transactions(initial);
        states.end(keep, () -> calls.add("commit"), () -> calls.add("cancel"));
        calls.add(states.state().name());
        return calls;
    }
}
