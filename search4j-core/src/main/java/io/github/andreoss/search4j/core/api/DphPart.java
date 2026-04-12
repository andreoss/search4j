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
 * Prepared DPH weight of a term.
 *
 * @param constant Base two logarithm of the total length over the
 *  collection frequency
 * @param scale Scaled within-query frequency
 * @param upper Upper bound on the weight
 * @since 0.1.0
 */
record DphPart(double constant, double scale, double upper)
    implements TermWeight {

    /**
     * Prepares the weight of a term.
     *
     * @param stats Collection and term statistics
     * @param factor Scale factor of the term
     * @return Term weight
     */
    static TermWeight prepare(final WeightStats stats, final double factor) {
        final double wdfup = stats.term().wdfMax().value();
        final TermWeight part;
        if (wdfup == 0.0) {
            part = new FixedPart(0.0);
        } else {
            final double lenup = stats.collection().bounds().docMax().value();
            final double constant = WeightMath.logTwo(
                stats.collection().total().value()
                    / (double) stats.term().collFreq().value()
            );
            final double scale = stats.term().wqf().value() * factor;
            final double half = Math.min(wdfup, lenup / 2.0);
            final double product = Math.min(
                wdfup * (1.0 - 1.0 / lenup), half * (1.0 - half / lenup)
            );
            final double root = Math.max(
                1.0,
                Math.min(wdfup, 0.25 * (Math.sqrt(8.0 * lenup + 9.0) - 3.0))
            );
            final double rest = 1.0 - root / lenup;
            part = new DphPart(
                constant, scale,
                Math.max(
                    0.0,
                    scale * rest * rest * (root / (root + 1.0))
                        * (constant + 0.5 * WeightMath.logTwo(2.0 * Math.PI * product))
                )
            );
        }
        return part;
    }

    @Override
    public double sumPart(final TermCount wdf, final DocStats doc) {
        final int count = wdf.value();
        final int len = doc.length().value();
        double result = 0.0;
        if (count != 0 && count != len) {
            final double ratio = (double) count / len;
            final double rest = 1.0 - ratio;
            final double weight = rest * rest / WeightMath.unsigned(count + 1)
                * (count * (WeightMath.logTwo(ratio) + this.constant)
                + 0.5 * WeightMath.logTwo(2.0 * Math.PI * count * (1.0 - ratio)));
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
