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
        return DphPart.of(DfrStats.of(stats, factor));
    }

    /**
     * Prepares the weight of a term from its statistics as numbers.
     *
     * @param input Statistics as numbers
     * @return Term weight
     */
    static TermWeight of(final DfrStats input) {
        final double wdfup = input.wdfup();
        final double lenup = input.lenup();
        final double scale = input.scale();
        final TermWeight part;
        if (wdfup == 0.0) {
            part = new FixedPart(0.0);
        } else {
            final double constant = WeightMath.logTwo(input.mean());
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
        return this.sum(
            WeightMath.unsigned(wdf.value()), WeightMath.unsigned(doc.length().value())
        );
    }

    /**
     * Weight of a document from its counts as numbers.
     *
     * @param count Within-document frequency
     * @param len Document length
     * @return Weight, zero when not positive
     */
    double sum(final double count, final double len) {
        double result = 0.0;
        if (count != 0 && count != len) {
            final double ratio = count / len;
            final double rest = 1.0 - ratio;
            final double weight = rest * rest / (count + 1.0)
                * (count * (WeightMath.logTwo(ratio) + this.constant)
                + 0.5 * WeightMath.logTwo(2.0 * Math.PI * count * (1.0 - ratio)));
            result = this.scale * weight;
            if (weight <= 0.0) {
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
