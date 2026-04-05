/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.TermCount;

/**
 * IDF and within-document frequency normalisation functions of the weight
 * schemes.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class WeightNorms {

    /**
     * Utility class, no instances.
     */
    private WeightNorms() {
    }

    /**
     * Computes the inverse document frequency.
     *
     * @param docs Total number of documents
     * @param freq Number of documents containing the term
     * @return Inverse document frequency
     */
    public static double idf(final TermCount docs, final TermCount freq) {
        final double total = docs.value();
        final double count = freq.value();
        double idf = 0.0;
        if (count > 0.0 && total > 0.0) {
            idf = Math.log((total - count + 0.5) / (count + 0.5));
        }
        return idf;
    }

    /**
     * Computes the PL2 normalised within-document frequency.
     *
     * @param wdf Within-document frequency
     * @param doclen Document length
     * @param avglen Average document length
     * @return Normalised within-document frequency
     */
    public static double wdfPl(
        final TermCount wdf, final TermCount doclen, final TermCount avglen
    ) {
        final double value = wdf.value();
        final double length = doclen.value();
        final double avg = avglen.value();
        double norm = 0.0;
        if (value > 0.0 && length > 0.0 && avg > 0.0) {
            norm = value * Math.log(1.0 + avg / length) / Math.log(2.0);
        }
        return norm;
    }

    /**
     * Computes the DPH normalised within-document frequency.
     *
     * @param wdf Within-document frequency
     * @param doclen Document length
     * @param avglen Average document length
     * @return Normalised within-document frequency
     */
    public static double wdfDph(
        final TermCount wdf, final TermCount doclen, final TermCount avglen
    ) {
        final double value = wdf.value();
        final double length = doclen.value();
        final double avg = avglen.value();
        double norm = 0.0;
        if (value > 0.0 && length > 0.0 && avg > 0.0) {
            final double first = value * Math.log(1.0 + avg / length)
                / Math.log(2.0);
            final double second = 0.5
                * Math.log(2.0 * Math.PI * value) / Math.log(2.0);
            norm = first + second;
        }
        return norm;
    }

    /**
     * Computes the DLH normalised within-document frequency.
     *
     * @param wdf Within-document frequency
     * @param doclen Document length
     * @param avglen Average document length
     * @return Normalised within-document frequency
     */
    public static double wdfDlh(
        final TermCount wdf, final TermCount doclen, final TermCount avglen
    ) {
        final double value = wdf.value();
        final double length = doclen.value();
        final double avg = avglen.value();
        double norm = 0.0;
        if (value > 0.0 && length > 0.0 && avg > 0.0) {
            norm = value * Math.log(avg / length) / Math.log(2.0);
        }
        return norm;
    }

    /**
     * Computes the BM25 normalised within-document frequency.
     *
     * @param wdf Within-document frequency
     * @param doclen Document length
     * @param norm Length normalisation of the scheme
     * @return Normalised within-document frequency
     */
    static double bmNorm(
        final TermCount wdf, final TermCount doclen, final Bm25Norm norm
    ) {
        final double value = wdf.value();
        final double length = doclen.value();
        final double avg = norm.avglen().value();
        double result = 0.0;
        final double denom = value
            + norm.sat() * (1.0 - norm.bpar() + norm.bpar() * length / avg);
        if (value > 0.0 && avg > 0.0 && denom > 0.0) {
            result = value / denom;
        }
        return result;
    }
}
