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
 * Prepared weight of a term under a scheme with an after-effect on the
 * normalised frequency and a constant information content.
 *
 * @param length Normalisation parameter times the average length
 * @param effect Multiplier of the after-effect
 * @param scale Scaled information content
 * @param upper Upper bound on the weight
 * @since 0.1.0
 */
record EffectPart(double length, double effect, double scale, double upper)
    implements TermWeight {

    /**
     * Normalised upper bound on the within-document frequency.
     *
     * @param stats Collection and term statistics
     * @param coef Document length normalisation parameter
     * @return Normalised bound
     */
    static double highest(final WeightStats stats, final double coef) {
        return stats.term().wdfMax().value() * WeightMath.logTwo(
            1.0 + coef * stats.collection().average()
                / stats.collection().bounds().docMin().value()
        );
    }

    @Override
    public double sumPart(final TermCount wdf, final DocStats doc) {
        double result = 0.0;
        if (wdf.value() != 0) {
            final double norm = wdf.value()
                * WeightMath.logTwo(1.0 + this.length / doc.length().value());
            result = norm * this.effect / (norm + 1.0) * this.scale;
        }
        return result;
    }

    @Override
    public double maxPart() {
        return this.upper;
    }
}
