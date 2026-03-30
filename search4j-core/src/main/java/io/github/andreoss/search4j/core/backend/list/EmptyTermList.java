/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Iterator bases shared by the database backends.
 */
package io.github.andreoss.search4j.core.backend.list;

import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Term list holding no term.
 *
 * @since 0.1.0
 */
public enum EmptyTermList implements AllTermsList {

    /**
     * The single empty list.
     */
    INSTANCE;

    @Override
    public TermCount approxSize() {
        return TermCount.ZERO;
    }

    @Override
    public String term() {
        throw new InvalidOperationError("An empty term list has no term");
    }

    @Override
    public TermCount termFrequency() {
        throw new InvalidOperationError("An empty term list has no term");
    }

    @Override
    public TermList next() {
        return this;
    }

    @Override
    public TermList skipTo(final String target) {
        return this;
    }

    @Override
    public boolean atEnd() {
        return true;
    }
}
