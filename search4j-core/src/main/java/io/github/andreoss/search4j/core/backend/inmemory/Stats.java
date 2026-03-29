/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Liveness and term statistics behind a term iterator.
 *
 * @since 0.1.0
 */
interface Stats {

    /**
     * Ensures the source is still open.
     *
     * <p>A closed source raises a database-closed error.</p>
     *
     * @return True when open
     */
    boolean live();

    /**
     * Number of documents indexed by a term.
     *
     * @param term The term text
     * @return Term frequency
     */
    TermCount frequency(String term);
}
