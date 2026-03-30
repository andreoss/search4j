/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Iterator bases shared by the database backends.
 */
package io.github.andreoss.search4j.core.backend.list;

import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.error.InvariantError;
import io.github.andreoss.search4j.core.error.UnimplementedError;
import java.util.function.Consumer;

/**
 * Access to the positions of the current document of a posting list.
 *
 * @since 0.1.0
 */
public interface PostingPositions {

    /**
     * Reads the positions at the current document into a list owned by
     * this posting list.
     *
     * <p>Only lists over a single term support it; others raise an
     * unimplemented error.</p>
     *
     * @return Positions, valid until the list moves
     */
    default PositionList readPositionList() {
        throw new UnimplementedError(
            "Positional operators only support single-term subqueries"
        );
    }

    /**
     * Opens the positions at the current document as an independent list.
     *
     * <p>Lists without positions raise an invalid-operation error.</p>
     *
     * @return Positions owned by the caller
     */
    default PositionList openPositionList() {
        throw new InvalidOperationError(
            "Positions are not meaningful for this posting list"
        );
    }

    /**
     * Hands the position lists of every leaf under this list to a sink.
     *
     * <p>Lists that are not part of a positional subtree raise an invariant
     * error.</p>
     *
     * @param sink Receiver of each leaf's positions
     */
    default void gatherPositionLists(final Consumer<PositionList> sink) {
        throw new InvariantError("Position lists are not gathered here");
    }
}
