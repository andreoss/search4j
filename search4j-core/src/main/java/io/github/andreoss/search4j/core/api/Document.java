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
 * A document: terms with positions, values in numbered slots, and opaque data.
 *
 * <p>A document is held by a database; its identity is a document number. The
 * data and value slots are plain text, while terms carry within-document
 * frequencies and positions.</p>
 *
 * @since 0.1.0
 */
public interface Document {

    /**
     * Returns the opaque data associated with the document.
     *
     * @return The data text
     */
    String data();

    /**
     * Replaces the opaque data associated with the document.
     *
     * @param data The new data text
     */
    void setData(String data);

    /**
     * Returns the value stored in a slot.
     *
     * @param slot The value slot number
     * @return The value, empty when the slot has no value
     */
    Optional<String> value(ValueNo slot);

    /**
     * Stores a value in a numbered slot.
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
     * Adds a term, increasing its within-document frequency.
     *
     * @param term The term text
     * @param increment The frequency increment
     */
    void addTerm(String term, TermCount increment);

    /**
     * Decreases a term frequency, removing the term at zero.
     *
     * @param term The term text
     * @param decrement The frequency decrement
     */
    void removeTerm(String term, TermCount decrement);

    /**
     * Removes every term from the document.
     */
    void clearTerms();

    /**
     * Returns the number of distinct terms in the document.
     *
     * @return The term count
     */
    TermCount termCount();

    /**
     * Returns the number of slots holding a value.
     *
     * @return The value count
     */
    TermCount valueCount();
}
