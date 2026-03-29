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
 * Statistics of the metadata key listing of a store.
 *
 * @param store The store holding the keys
 * @since 0.1.0
 */
record Keys(Store store) implements Stats {

    @Override
    public boolean live() {
        return this.store.live();
    }

    @Override
    public TermCount frequency(final String term) {
        throw new InvalidOperationError(
            "Term frequency is not meaningful for metadata keys"
        );
    }
}
