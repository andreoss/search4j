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
 * Prepared weight of a term under a BM25 scheme.
 *
 * @param params Scheme parameters
 * @param weight Term weight
 * @param coef Multiplier of the within-document frequency
 * @param delta Addend of the saturated frequency
 * @param factor Reciprocal of the average length, zero when unused
 * @param wdfmax Upper bound on the within-document frequency
 * @param docmin Lower bound on the document length
 * @since 0.1.0
 */
record Bm25Part(
    Bm25Params params, double weight, double coef, double delta,
    double factor, TermCount wdfmax, TermCount docmin
) implements TermWeight {

    @Override
    public boolean needsDocLength() {
        return this.params.termsat() != 0.0 && this.params.slope() != 0.0
            || this.params.correction() != 0.0;
    }

    @Override
    public double sumPart(final TermCount wdf, final DocStats doc) {
        return this.saturated((double) wdf.value(), (double) doc.length().value());
    }

    @Override
    public double maxPart() {
        final long most = this.wdfmax.value();
        return this.saturated((double) most, (double) Math.max(most, this.docmin.value()));
    }

    private double saturated(final double freq, final double len) {
        return this.weight * (this.coef * freq / this.denom(freq, len) + this.delta);
    }

    private double denom(final double freq, final double len) {
        return this.params.denominator(this.params.normal(len, this.factor)) + freq;
    }
}
