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
 * Prepared BB2 weight of a term.
 *
 * @param length Normalisation parameter times the average length
 * @param coll Collection frequency of the term
 * @param size Number of documents
 * @param effect Scaled Bernoulli after-effect numerator
 * @param stirling Constant term and the two Stirling logarithms
 * @param upper Upper bound on the weight
 * @since 0.1.0
 */
record Bb2Part(
    double length, double coll, long size, double effect, Stirling stirling,
    double upper
) implements TermWeight {

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
            final double length = coef * stats.collection().average();
            final LengthBounds bounds = stats.collection().bounds();
            final double lower = WeightMath.logTwo(
                1.0 + length / bounds.docMax().value()
            );
            final double coll = WeightMath.unsigned(stats.term().collFreq().value());
            double upper = stats.term().wdfMax().value()
                * WeightMath.logTwo(1.0 + length / bounds.docMin().value());
            if (lower >= coll - 1.0 || upper >= coll - 1.0) {
                upper = coll - 1.0;
            }
            final double effect = stats.term().wqf().value() * factor
                * (coll + 1.0) / WeightMath.unsigned(stats.term().freq().value());
            final long size = stats.collection().size().value();
            final Stirling stirling = Stirling.prepare(size, coll);
            double bound = effect / (lower + 1.0) * stirling.bound(lower, upper);
            if (bound < 0.0) {
                bound = 0.0;
            }
            part = new Bb2Part(length, coll, size, effect, stirling, bound);
        }
        return part;
    }

    @Override
    public double sumPart(final TermCount wdf, final DocStats doc) {
        double result = 0.0;
        if (wdf.value() != 0) {
            double norm = wdf.value()
                * WeightMath.logTwo(1.0 + this.length / doc.length().value());
            if (norm >= this.coll - 1.0) {
                norm = this.coll - 1.0;
            }
            result = this.effect / (norm + 1.0)
                * this.stirling.weight(norm, this.size);
            if (result < 0.0) {
                result = 0.0;
            }
        }
        return result;
    }

    @Override
    public double maxPart() {
        return this.upper;
    }
}
