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
import java.util.List;
import java.util.Optional;

/**
 * Terms in ascending byte order, of one document or of a whole database.
 *
 * <p>The list starts before its first term, so a move must come before any
 * call that needs a current term. A move returns the list to carry on with:
 * the same list, or a replacement the caller adopts.</p>
 *
 * @since 0.1.0
 */
public interface TermList {

    /**
     * Number of terms, possibly an estimate.
     *
     * @return Term count
     */
    TermCount approxSize();

    /**
     * Term the list is on.
     *
     * @return Current term
     */
    String term();

    /**
     * Within-document frequency of the current term.
     *
     * @return Frequency in the document listed
     */
    TermCount wdf();

    /**
     * Number of documents indexed by the current term.
     *
     * @return Term frequency in the database
     */
    TermCount termFrequency();

    /**
     * Moves to the next term.
     *
     * @return List to continue with
     */
    TermList next();

    /**
     * Moves to the first term at or after a target, never backwards.
     *
     * @param target Lowest term wanted
     * @return List to continue with
     */
    TermList skipTo(String target);

    /**
     * Tests whether the list is exhausted.
     *
     * @return True once moved past the last term
     */
    boolean atEnd();

    /**
     * Number of positions of the current term.
     *
     * @return Position count, zero when unpositioned
     */
    TermCount positionCount();

    /**
     * Opens the positions of the current term.
     *
     * @return Positions owned by the caller
     */
    PositionList positionList();

    /**
     * Positions of the current term when the list holds them as a list
     * already, sparing a copy.
     *
     * @return Ascending positions, empty when not held that way
     */
    default Optional<List<TermPos>> heldPositions() {
        return Optional.empty();
    }
}
