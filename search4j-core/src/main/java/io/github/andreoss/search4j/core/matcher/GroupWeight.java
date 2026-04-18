/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Matcher core of the search engine.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.backend.list.TermWeight;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Term weight of the search scheme scoring a synonym group as one term.
 *
 * @param weight Term weight prepared from the estimated group frequencies
 * @since 0.10.0
 */
record GroupWeight(TermWeight weight) implements SynonymWeight {

    @Override
    public double sumPart(final TermCount wdf, final DocStats doc) {
        return this.weight.sumPart(wdf, doc);
    }

    @Override
    public double maxPart() {
        return this.weight.maxPart();
    }

    @Override
    public boolean needsWdf() {
        return this.weight.needsWdf();
    }

    @Override
    public boolean needsWdfDocMax() {
        return this.weight.needsWdfDocMax();
    }

    @Override
    public boolean needsDocLength() {
        return this.weight.needsDocLength();
    }
}
