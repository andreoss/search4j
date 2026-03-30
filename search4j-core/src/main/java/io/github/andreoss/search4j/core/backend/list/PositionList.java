/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Iterator bases shared by the database backends.
 */
package io.github.andreoss.search4j.core.backend.list;

import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;

/**
 * Positions of a term in one document, read forward in ascending order.
 *
 * <p>The list starts before its first position, so {@link #next()} or
 * {@link #skipTo(TermPos)} must be called before {@link #position()}.</p>
 *
 * @since 0.1.0
 */
public interface PositionList {

    /**
     * Number of positions, possibly an estimate.
     *
     * @return Position count
     */
    TermCount approxSize();

    /**
     * Highest position in the list.
     *
     * @return Last position, {@link TermPos#FIRST} when the list is empty
     */
    TermPos back();

    /**
     * Position the list is on.
     *
     * @return Current position
     */
    TermPos position();

    /**
     * Moves to the next position.
     *
     * @return True when a position was reached, false at the end
     */
    boolean next();

    /**
     * Moves to the first position at or after a target, never backwards.
     *
     * @param target Lowest position wanted
     * @return True when a position was reached, false at the end
     */
    boolean skipTo(TermPos target);
}
