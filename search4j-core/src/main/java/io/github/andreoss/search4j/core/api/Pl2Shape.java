/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Constants of the Poisson model shared by the PL2 schemes.
 *
 * @param length Normalisation parameter times the average length
 * @param first Constant term of the Poisson information
 * @param second Multiplier of the normalised frequency
 * @since 0.1.0
 */
record Pl2Shape(double length, double first, double second) {

    /**
     * Inverse of the natural logarithm of two.
     */
    private static final double BASE = 1.0 / Math.log(2.0);

    /**
     * Computes the constants.
     *
     * @param stats Collection and term statistics
     * @param coef Document length normalisation parameter
     * @return The constants
     */
    static Pl2Shape prepare(final WeightStats stats, final double coef) {
        final double mean = Pl2Shape.mean(stats);
        return new Pl2Shape(
            coef * stats.collection().average(),
            mean * Pl2Shape.BASE + 0.5 * WeightMath.logTwo(2.0 * Math.PI),
            WeightMath.logTwo(mean) + Pl2Shape.BASE
        );
    }

    /**
     * Mean frequency of the term per document.
     *
     * @param stats Collection and term statistics
     * @return Collection frequency over collection size
     */
    static double mean(final WeightStats stats) {
        return WeightMath.unsigned(stats.term().collFreq().value())
            / WeightMath.unsigned(stats.collection().size().value());
    }

    /**
     * Normalised within-document frequency.
     *
     * @param wdf Within-document frequency
     * @param doc Document statistics
     * @return Normalised frequency
     */
    double normal(final TermCount wdf, final DocStats doc) {
        return wdf.value()
            * WeightMath.logTwo(1.0 + this.length / doc.length().value());
    }

    /**
     * Poisson information of a normalised frequency.
     *
     * @param norm Normalised frequency
     * @return Information
     */
    double poisson(final double norm) {
        return this.first + (norm + 0.5) * WeightMath.logTwo(norm)
            - this.second * norm;
    }

    /**
     * Upper bound on the weight before scaling, without the shift.
     *
     * @param stats Collection and term statistics
     * @return Bound
     */
    double bound(final WeightStats stats) {
        final LengthBounds bounds = stats.collection().bounds();
        final int wdfmax = stats.term().wdfMax().value();
        final double lower = WeightMath.logTwo(
            1.0 + this.length / bounds.docMax().value()
        );
        final double upper = wdfmax * WeightMath.logTwo(
            1.0 + this.length / Math.max(wdfmax, bounds.docMin().value())
        );
        double best = lower;
        if (this.first + this.second > 0.0) {
            best = upper;
        }
        return (upper + 0.5) * WeightMath.logTwo(upper) / (upper + 1.0)
            + (this.first - this.second * best) / (best + 1.0);
    }
}
