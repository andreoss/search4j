/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.Optional;

/**
 * Values of a document, held in numbered slots.
 *
 * @since 0.1.0
 */
public interface DocumentValues {

    /**
     * Value stored in a slot.
     *
     * @param slot Slot to read
     * @return Value, empty when the slot has none
     */
    Optional<String> value(ValueNo slot);

    /**
     * Stores a value in a numbered slot; an empty value clears the slot.
     *
     * @param slot Slot to write
     * @param value Value to store, empty to clear
     */
    void addValue(ValueNo slot, String value);

    /**
     * Removes the value from a numbered slot.
     *
     * @param slot Slot to clear
     */
    void removeValue(ValueNo slot);

    /**
     * Removes every value.
     */
    void clearValues();

    /**
     * Number of slots holding a value.
     *
     * @return Slot count, zero when none is set
     */
    TermCount valueCount();

    /**
     * Opens the values in ascending slot order.
     *
     * @return Iterator over a snapshot of the values
     */
    ValueIterator values();
}
