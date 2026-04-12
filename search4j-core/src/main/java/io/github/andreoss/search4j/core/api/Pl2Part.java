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
 * Prepared PL2 weight of a term.
 *
 * @param shape Normalised length and Poisson constants
 * @param scale Scaled within-query frequency
 * @param upper Upper bound on the weight
 * @since 0.1.0
 */
record Pl2Part(Pl2Shape shape, double scale, double upper) implements TermWeight {

    /**
     * Prepares the weight of a term.
     *
     * @param stats Collection and term statistics
     * @param factor Scale factor of the term
     * @param coef Document length normalisation parameter
     * @return Term weight
     */
    static TermWeight prepare(
        final WeightStats stats, final double factor, final double coef
    ) {
        final TermWeight part;
        if (stats.term().wdfMax().value() == 0) {
            part = new FixedPart(0.0);
        } else {
            final double scale = factor * stats.term().wqf().value();
            final Pl2Shape shape = Pl2Shape.prepare(stats, coef);
            double upper = scale * shape.bound(stats);
            if (upper <= 0.0) {
                upper = 0.0;
            }
            part = new Pl2Part(shape, scale, upper);
        }
        return part;
    }

    @Override
    public double sumPart(final TermCount wdf, final DocStats doc) {
        double result = 0.0;
        if (wdf.value() != 0) {
            final double norm = this.shape.normal(wdf, doc);
            final double poisson = this.shape.poisson(norm);
            if (poisson > 0.0) {
                result = this.scale * poisson / (norm + 1.0);
            }
        }
        return result;
    }

    @Override
    public double maxPart() {
        return this.upper;
    }
}
