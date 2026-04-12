/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

/**
 * Parameters of two-stage smoothing with the query length.
 *
 * @param lambda Interpolation weight of the collection model
 * @param mass Smoothing pseudo-length
 * @param query Length of the query
 * @since 0.1.0
 */
record TwoStage(double lambda, double mass, double query) {

    /**
     * Smoothed length of a document.
     *
     * @param len Document length
     * @return Interpolated length plus the pseudo-length
     */
    double smoothed(final double len) {
        return this.lambda * len + this.mass;
    }

    /**
     * Length weight of a document before the offset.
     *
     * @param len Document length
     * @return Weight
     */
    double length(final double len) {
        return this.query * Math.log(this.smoothed(len) / (len + this.mass));
    }
}
