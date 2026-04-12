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
     * Utility class, no instances.
     */
    private WeightMath() {
    }

    /**
     * Base two logarithm.
     *
     * @param value Argument
     * @return Logarithm
     */
    static double logTwo(final double value) {
        return Math.log(value) / WeightMath.LN2;
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
     * Unsigned value of a 32-bit count, as the reference holds counts.
     *
     * @param value Count, possibly wrapped
     * @return Value between zero and two to the thirty-second
     */
    static double unsigned(final int value) {
        return Integer.toUnsignedLong(value);
    }
}
