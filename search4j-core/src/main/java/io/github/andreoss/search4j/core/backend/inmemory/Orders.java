/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.Comparator;

/**
 * Explicit key orderings for the sorted maps of the backend.
 *
 * @since 0.1.0
 */
final class Orders {

    /**
     * Ascending document identifiers.
     */
    static final Comparator<DocId> DOCS = Comparator.naturalOrder();

    /**
     * Ascending value slot numbers.
     */
    static final Comparator<ValueNo> SLOTS = Comparator.naturalOrder();

    /**
     * Utility class, no instances.
     */
    private Orders() {
    }
}
