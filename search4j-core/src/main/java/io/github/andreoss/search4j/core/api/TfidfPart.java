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
 * Prepared tf-idf weight of a term.
 *
 * @param wdf Within-document frequency normalisation
 * @param pivot Pivoted length normalisation
 * @param idf Normalised inverse document frequency
 * @param scale Scaled within-query frequency
 * @param wdfmax Upper bound on the within-document frequency
 * @param docmin Lower bound on the document length
 * @param length Whether a contribution depends on the document length
 * @since 0.1.0
 */
record TfidfPart(
    WdfNorm wdf, Pivot pivot, double idf, double scale, TermCount wdfmax,
    TermCount docmin, boolean length
) implements TermWeight {

    @Override
    public boolean needsDocLength() {
        return this.length;
    }

    @Override
    public boolean needsWdfDocMax() {
        return this.wdf == WdfNorm.MAX || this.wdf == WdfNorm.AUG;
    }

    @Override
    public double sumPart(final TermCount freq, final DocStats doc) {
        return this.wdf.apply(freq, doc, this.pivot) * this.idf * this.scale;
    }

    @Override
    public double maxPart() {
        return this.sumPart(
            this.wdfmax, new DocStats(this.docmin, this.docmin, this.wdfmax)
        );
    }
}
