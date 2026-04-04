/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.ShardStatistics;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * IDF and WDF normalisation functions for weight schemes.
 *
 * <p>Provides standard normalisation functions used by various
 * weighting schemes.</p>
 *
 * @since 0.1.0
 */
public final class WeightNorms {

    /**
     * Utility class, no instances.
     */
    private WeightNorms() {}

    /**
     * Computes IDF: log((N - n + 0.5) / (n + 0.5)).
     *
     * @param docCount Total number of documents
     * @param termFreq Number of documents containing the term
     * @return IDF value
     */
    public static double idf(final TermCount docCount, final TermCount termFreq) {
        final double n = docCount.value();
        final double nt = termFreq.value();
        if (nt <= 0.0 || n <= 0.0) {
            return 0.0;
        }
        return Math.log((n - nt + 0.5) / (nt + 0.5));
    }

    /**
     * Computes normalised WDF: wdf / (wdf + k * (1 - b + b * docLength / avgLength)).
     *
     * @param wdf Within-document frequency
     * @param docLength Document length
     * @param avgLength Average document length
     * @param k BM25 k parameter
     * @param b BM25 b parameter
     * @return Normalised WDF
     */
    public static double wdfNormalised(
        final TermCount wdf, final TermCount docLength,
        final TermCount avgLength, final double k, final double b
    ) {
        final double wdfVal = wdf.value();
        final double dl = docLength.value();
        final double avgl = avgLength.value();
        if (wdfVal <= 0.0 || avgl <= 0.0) {
            return 0.0;
        }
        final double denom = wdfVal + k * (1.0 - b + b * dl / avgl);
        return denom > 0.0 ? wdfVal / denom : 0.0;
    }

    /**
     * Computes PL2 normalised WDF: wdf * log2(1 + avgLength / docLength).
     *
     * @param wdf Within-document frequency
     * @param docLength Document length
     * @param avgLength Average document length
     * @return Normalised WDF
     */
    public static double wdfPL2(
        final TermCount wdf, final TermCount docLength,
        final TermCount avgLength
    ) {
        final double wdfVal = wdf.value();
        final double dl = docLength.value();
        final double avgl = avgLength.value();
        if (wdfVal <= 0.0 || dl <= 0.0 || avgl <= 0.0) {
            return 0.0;
        }
        return wdfVal * Math.log(1.0 + avgl / dl) / Math.log(2.0);
    }

    /**
     * Computes DPH normalised WDF: wdf * log2(1 + avgLength / docLength) + 0.5 * log2(2 * pi * wdf).
     *
     * @param wdf Within-document frequency
     * @param docLength Document length
     * @param avgLength Average document length
     * @return Normalised WDF
     */
    public static double wdfDPH(
        final TermCount wdf, final TermCount docLength,
        final TermCount avgLength
    ) {
        final double wdfVal = wdf.value();
        final double dl = docLength.value();
        final double avgl = avgLength.value();
        if (wdfVal <= 0.0 || dl <= 0.0 || avgl <= 0.0) {
            return 0.0;
        }
        final double part1 = wdfVal * Math.log(1.0 + avgl / dl) / Math.log(2.0);
        final double part2 = 0.5 * Math.log(2.0 * Math.PI * wdfVal) / Math.log(2.0);
        return part1 + part2;
    }

    /**
     * Computes DLH normalised WDF: wdf * log2(avgLength / docLength).
     *
     * @param wdf Within-document frequency
     * @param docLength Document length
     * @param avgLength Average document length
     * @return Normalised WDF
     */
    public static double wdfDLH(
        final TermCount wdf, final TermCount docLength,
        final TermCount avgLength
    ) {
        final double wdfVal = wdf.value();
        final double dl = docLength.value();
        final double avgl = avgLength.value();
        if (wdfVal <= 0.0 || dl <= 0.0 || avgl <= 0.0) {
            return 0.0;
        }
        return wdfVal * Math.log(avgl / dl) / Math.log(2.0);
    }
}
