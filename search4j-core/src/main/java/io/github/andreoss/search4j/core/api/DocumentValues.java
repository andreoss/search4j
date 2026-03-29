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
     * Returns the value stored in a slot.
     *
     * @param slot The value slot number
     * @return The value, empty when the slot has no value
     */
    Optional<String> value(ValueNo slot);

    /**
     * Stores a value in a numbered slot; an empty value clears the slot.
     *
     * @param slot The value slot number
     * @param value The value text
     */
    void addValue(ValueNo slot, String value);

    /**
     * Removes the value from a numbered slot.
     *
     * @param slot The value slot number
     */
    void removeValue(ValueNo slot);

    /**
     * Removes every value.
     */
    void clearValues();

    /**
     * Returns the number of slots holding a value.
     *
     * @return The value count
     */
    TermCount valueCount();

    /**
     * Opens the values in ascending slot order.
     *
     * @return The value iterator
     */
    ValueIterator values();
}
