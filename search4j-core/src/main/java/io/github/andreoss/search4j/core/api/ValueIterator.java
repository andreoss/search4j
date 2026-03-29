/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.ValueNo;

/**
 * Iterator over values: the slots of one document in ascending slot order, or
 * one slot across documents in ascending identifier order.
 *
 * <p>The accessors describe the value most recently returned by
 * {@link #next()}. Over a single document, {@link #docId()} and
 * {@link #skipTo(DocId)} raise an invalid-operation error.</p>
 *
 * @since 0.1.0
 */
public interface ValueIterator extends AutoCloseable {

    /**
     * Tests whether another value is available.
     *
     * @return True unless the values are exhausted
     */
    boolean hasNext();

    /**
     * Advances to the next value and returns it.
     *
     * @return Non-empty value
     */
    String next();

    /**
     * Slot of the current value.
     *
     * @return Slot holding the value
     */
    ValueNo slot();

    /**
     * Document holding the current value.
     *
     * @return Identifier of the document
     */
    DocId docId();

    /**
     * Skips the documents below a target, so the next value returned belongs
     * to the first document at or after it.
     *
     * @param target Lowest document identifier wanted
     */
    void skipTo(DocId target);

    @Override
    void close();
}
