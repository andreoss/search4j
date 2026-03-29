/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Statistics of a document that belongs to no database.
 *
 * @since 0.1.0
 */
enum Detached implements Stats {

    /**
     * The single instance.
     */
    INSTANCE;

    @Override
    public boolean live() {
        return true;
    }

    @Override
    public TermCount frequency(final String term) {
        throw new InvalidOperationError(
            "Term frequency needs a document held by a database"
        );
    }
}
