/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

/**
 * Stirling approximations of the Bose-Einstein model of the BB2 scheme.
 *
 * @param base Constant term of the information content
 * @param coll Collection frequency of the term
 * @param size Number of documents, at least two
 * @param first Logarithm of documents plus frequency minus one
 * @param second Logarithm of the collection frequency
 * @since 0.1.0
 */
record Stirling(
    double base, double coll, double size, double first, double second
) {

    /**
     * Computes the constants.
     *
     * @param size Number of documents
     * @param coll Collection frequency of the term
     * @return The constants
     */
    static Stirling prepare(final long size, final double coll) {
        double docs = 2.0;
        if (size > 2L) {
            docs = size;
        }
        return new Stirling(
            -1.0 / Math.log(2.0) - WeightMath.logTwo(docs - 1.0), coll, docs,
            WeightMath.logTwo(docs + coll - 1.0), WeightMath.logTwo(coll)
        );
    }

    /**
     * Information content before the after-effect, at its upper bound.
     *
     * @param lower Lower bound on the normalised frequency
     * @param upper Upper bound on the normalised frequency
     * @return Information content
     */
    double bound(final double lower, final double upper) {
        return this.base
            + (Stirling.value(upper + 1.0, this.size + this.coll - lower - 2.0, this.first)
            - Stirling.value(lower, this.coll - upper, this.second));
    }

    /**
     * Information content before the after-effect.
     *
     * @param norm Normalised frequency
     * @param count Number of documents
     * @return Information content
     */
    double weight(final double norm, final long count) {
        double less = 0.0;
        if (count > 2L) {
            less = count - 2L;
        }
        final double rest = this.coll - norm;
        return this.base
            + (Stirling.value(norm + 1.0, less + rest, this.first)
            - Stirling.value(norm, rest, this.second));
    }

    private static double value(
        final double diff, final double rest, final double constant
    ) {
        return (rest + 0.5) * (constant - WeightMath.logTwo(rest))
            + diff * constant;
    }
}
