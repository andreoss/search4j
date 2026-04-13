/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

/**
 * Arithmetic shared by the weighting schemes.
 *
 * @since 0.1.0
 */
final class WeightMath {

    /**
     * Natural logarithm of two.
     */
    private static final double LN2 = Math.log(2.0);

    /**
     * Reciprocal of the natural logarithm of two, rounded.
     */
    private static final double INV_HI = 0x1.71547652B82FEp0;

    /**
     * Square root of two, the upper end of the reduced argument.
     */
    private static final double ROOT = Math.sqrt(2.0);

    /**
     * Coefficients 1/(2k+1) of the inverse hyperbolic tangent series,
     * highest first.
     */
    private static final double[] SERIES = {
        1.0 / 25.0, 1.0 / 23.0, 1.0 / 21.0, 1.0 / 19.0, 1.0 / 17.0,
        1.0 / 15.0, 1.0 / 13.0, 1.0 / 11.0, 1.0 / 9.0, 1.0 / 7.0, 1.0 / 5.0,
        1.0 / 3.0,
    };

    /**
     * Utility class, no instances.
     */
    private WeightMath() {
    }

    /**
     * Base two logarithm, nearly correctly rounded as the reference library
     * computes it: the binary exponent is split off exactly and the
     * logarithm of the reduced argument is carried in double-double
     * precision, so that differences of large logarithms keep their digits.
     *
     * @param value Argument
     * @return Logarithm
     */
    static double logTwo(final double value) {
        double result = Math.log(value) / WeightMath.LN2;
        if (value >= Double.MIN_NORMAL && value < Double.POSITIVE_INFINITY) {
            int exponent = Math.getExponent(value);
            double reduced = Math.scalb(value, -exponent);
            if (reduced > WeightMath.ROOT) {
                reduced = reduced / 2.0;
                exponent = exponent + 1;
            }
            final double[] log = WeightMath.natural(reduced);
            final double high = log[0] * WeightMath.INV_HI;
            final double low = Math.fma(log[0], WeightMath.INV_HI, -high)
                + log[0] * 0x1.777D0FFDA0D24p-56 + log[1] * WeightMath.INV_HI;
            final double sum = exponent + high;
            result = sum + (high - (sum - exponent) + low);
        }
        return result;
    }

    /**
     * Larger of two values, the first unless it is below the second.
     *
     * @param first First value
     * @param second Second value
     * @return The larger value
     */
    static double larger(final double first, final double second) {
        double result = first;
        if (first < second) {
            result = second;
        }
        return result;
    }

    /**
     * Value of a count computed in unsigned 32-bit arithmetic, kept to its
     * low 32 bits as the reference keeps counts.
     *
     * @param value Count or a sum or product of counts
     * @return Value between zero and two to the thirty-second
     */
    static double unsigned(final long value) {
        return value & 0xFFFF_FFFFL;
    }

    private static double remainder(final double value, final double ratio) {
        final double den = value + 1.0;
        return (Math.fma(-ratio, den, value - 1.0)
            - ratio * (value - (den - 1.0) + (1.0 - (den - (den - 1.0)))))
            / den;
    }

    private static double[] natural(final double value) {
        final double ratio = (value - 1.0) / (value + 1.0);
        final double square = ratio * ratio;
        double series = 0.0;
        for (final double coef : WeightMath.SERIES) {
            series = coef + square * series;
        }
        final double lead = 2.0 * ratio;
        final double more = lead * square * series;
        final double high = lead + more;
        return new double[] {
            high,
            more - (high - lead) + 2.0 * WeightMath.remainder(value, ratio),
        };
    }
}
