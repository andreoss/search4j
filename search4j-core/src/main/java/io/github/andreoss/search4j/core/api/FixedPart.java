/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.backend.list.TermWeight;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Term weight that is the same for every posting.
 *
 * @param value Weight of every posting
 * @since 0.1.0
 */
record FixedPart(double value) implements TermWeight {

    @Override
    public boolean needsWdf() {
        return false;
    }

    @Override
    public double sumPart(final TermCount wdf, final DocStats doc) {
        return this.value;
    }

    @Override
    public double maxPart() {
        return this.value;
    }
}
