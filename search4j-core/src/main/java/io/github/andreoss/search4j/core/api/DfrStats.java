/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

/**
 * Statistics of a divergence-from-randomness term weight as numbers.
 *
 * @param wdfup Upper bound on the within-document frequency
 * @param lenup Upper bound on the document length
 * @param lenlow Lower bound on the document length
 * @param mean Total length over the collection frequency
 * @param scale Scaled within-query frequency
 * @since 0.5.0
 */
record DfrStats(double wdfup, double lenup, double lenlow, double mean, double scale) {

    /**
     * Reads the statistics of a term.
     *
     * @param stats Collection and term statistics
     * @param factor Scale factor of the term
     * @return Statistics as numbers
     */
    static DfrStats of(final WeightStats stats, final double factor) {
        final LengthBounds bounds = stats.collection().bounds();
        return new DfrStats(
            WeightMath.unsigned(stats.term().wdfMax().value()),
            WeightMath.unsigned(bounds.docMax().value()),
            WeightMath.unsigned(bounds.docMin().value()),
            stats.collection().total().value()
                / WeightMath.unsigned(stats.term().collFreq().value()),
            WeightMath.unsigned(stats.term().wqf().value()) * factor
        );
    }
}
