/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Iterator bases shared by the database backends.
 */
package io.github.andreoss.search4j.core.backend.list;

import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.ValueNo;

/**
 * Values read forward: one slot across documents, or the slots of one
 * document.
 *
 * <p>The list starts before its first value, so a move must come before any
 * call that needs a current value.</p>
 *
 * @since 0.1.0
 */
public interface ValueList {

    /**
     * Document holding the current value.
     *
     * @return Document identifier
     */
    DocId docId();

    /**
     * Current value.
     *
     * @return Non-empty value
     */
    String value();

    /**
     * Slot of the current value.
     *
     * @return Slot number
     */
    ValueNo slot();

    /**
     * Tests whether the list is exhausted.
     *
     * @return True once moved past the last value
     */
    boolean atEnd();

    /**
     * Moves to the next value.
     */
    void next();

    /**
     * Moves to the first document at or after a target holding a value,
     * never backwards.
     *
     * @param target Lowest document wanted
     */
    void skipTo(DocId target);

    /**
     * Checks whether a document holds a value, moving only when that is
     * cheap.
     *
     * @param target Document to check
     * @return True when on the document or past it; false when the document
     *  holds no value and the position is unspecified
     */
    default boolean check(final DocId target) {
        this.skipTo(target);
        return true;
    }

    /**
     * Human-readable description for diagnostics.
     *
     * @return Description naming the list and its state
     */
    String description();
}
