/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Matcher core of the search engine.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.api.TermStats;
import io.github.andreoss.search4j.core.api.Weight;
import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.backend.list.TermWeight;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Weight adapter for a leaf term.
 *
 * @since 0.1.0
 */
final class LeafTermWeight implements TermWeight {

    /**
     * Term whose weight is computed.
     */
    private final String term;

    /**
     * Weight scheme of the search.
     */
    private final Weight weight;

    /**
     * Creates the adapter.
     *
     * @param term Term whose weight is computed
     * @param weight Weight scheme of the search
     */
    LeafTermWeight(final String term, final Weight weight) {
        this.term = term;
        this.weight = weight;
    }

    @Override
    public double sumPart(final TermCount wdf, final DocStats doc) {
        return this.weight.weight(
            this.term, new TermStats(TermCount.ONE, wdf), doc
        );
    }

    @Override
    public double maxPart() {
        return 1.0;
    }
}
