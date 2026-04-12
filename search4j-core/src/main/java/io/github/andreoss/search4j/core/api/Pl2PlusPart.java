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
 * Prepared PL2+ weight of a term.
 *
 * @param shape Normalised length and Poisson constants
 * @param scale Scaled within-query frequency
 * @param shift Weight added to every posting
 * @param upper Upper bound on the weight
 * @since 0.1.0
 */
record Pl2PlusPart(Pl2Shape shape, double scale, double shift, double upper)
    implements TermWeight {

    /**
     * Prepares the weight of a term.
     *
     * @param stats Collection and term statistics
     * @param factor Scale factor of the term
     * @param params Normalisation parameter and lower bound frequency
     * @return Term weight
     */
    static TermWeight prepare(
        final WeightStats stats, final double factor, final double... params
    ) {
        final TermWeight part;
        if (stats.term().wdfMax().value() == 0 || Pl2Shape.mean(stats) > 1.0) {
            part = new FixedPart(0.0);
        } else {
            final double scale = factor * stats.term().wqf().value();
            final Pl2Shape shape = Pl2Shape.prepare(stats, params[0]);
            final double shift = shape.poisson(params[1]) / (params[1] + 1.0);
            double upper = scale * (shape.bound(stats) + shift);
            if (upper < 0.0) {
                upper = 0.0;
            }
            part = new Pl2PlusPart(shape, scale, shift, upper);
        }
        return part;
    }

    @Override
    public double sumPart(final TermCount wdf, final DocStats doc) {
        double result = 0.0;
        if (wdf.value() != 0) {
            final double norm = this.shape.normal(wdf, doc);
            final double weight = this.shape.poisson(norm) / (norm + 1.0)
                + this.shift;
            if (weight > 0.0) {
                result = this.scale * weight;
            }
        }
        return result;
    }

    @Override
    public double maxPart() {
        return this.upper;
    }
}
