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
 * Prepared DLH weight of a term.
 *
 * @param constant Total length over the collection frequency
 * @param scale Scaled within-query frequency
 * @param upper Upper bound on the weight
 * @since 0.1.0
 */
record DlhPart(double constant, double scale, double upper)
    implements TermWeight {

    /**
     * Prepares the weight of a term.
     *
     * @param stats Collection and term statistics
     * @param factor Scale factor of the term
     * @return Term weight
     */
    static TermWeight prepare(final WeightStats stats, final double factor) {
        return DlhPart.of(DfrStats.of(stats, factor));
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
        final double lenlow = input.lenlow();
        final double constant = input.mean();
        final double scale = input.scale();
        final TermWeight part;
        if (wdfup == 0.0) {
            part = new FixedPart(0.0);
        } else {
            double ratio = 1.0;
            if (wdfup < lenlow) {
                ratio = wdfup / lenlow;
            }
            final double logged = ratio * constant;
            double first = 1.0;
            if (logged > 1.0) {
                first = wdfup;
            }
            double second = 0.0;
            if (lenlow > wdfup) {
                second = Math.max(
                    (lenlow - 1.0) * WeightMath.logTwo(1.0 - 1.0 / lenlow),
                    (lenlow - wdfup) * WeightMath.logTwo(1.0 - wdfup / lenlow)
                );
            }
            final double half = Math.min(wdfup, lenup / 2.0);
            final double bound = first / (first + 0.5) * WeightMath.logTwo(logged)
                + second
                + 0.5 * WeightMath.logTwo(half * (1.0 - half / lenup) * 2.0 * Math.PI)
                / 1.5;
            double upper = bound * scale;
            if (bound < 0.0) {
                upper = 0.0;
            }
            part = new DlhPart(constant, scale, upper);
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
            final double weight = count * WeightMath.logTwo(ratio * this.constant)
                + (len - count) * WeightMath.logTwo(rest)
                + 0.5 * WeightMath.logTwo(2.0 * Math.PI * count * rest);
            result = this.scale * weight / (count + 0.5);
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
