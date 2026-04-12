/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

/**
 * Parameters of the BM25 schemes; negative values are raised to zero and
 * the slope is kept between zero and one.
 *
 * @param termsat Saturation of the within-document frequency
 * @param correction Weight of the document length correction
 * @param querysat Saturation of the within-query frequency
 * @param slope Strength of the document length normalisation
 * @param floor Least normalised document length
 * @since 0.1.0
 */
public record Bm25Params(
    double termsat, double correction, double querysat, double slope,
    double floor
) {

    /**
     * Default parameters.
     */
    public static final Bm25Params DEFAULT = new Bm25Params(
        1.0, 0.0, 1.0, 0.5, 0.5
    );

    /**
     * Applies the bounds of the parameters.
     *
     * @param termsat Saturation of the within-document frequency
     * @param correction Weight of the document length correction
     * @param querysat Saturation of the within-query frequency
     * @param slope Strength of the document length normalisation
     * @param floor Least normalised document length
     */
    public Bm25Params {
        termsat = new Clamped(termsat).value();
        correction = new Clamped(correction).value();
        querysat = new Clamped(querysat).value();
        slope = new Clamped(slope).value();
        if (slope > 1.0) {
            slope = 1.0;
        }
    }

    /**
     * Encodes the parameters.
     *
     * @return Their bytes
     */
    byte[] bytes() {
        return WeightBytes.doubles(
            this.termsat, this.correction, this.querysat, this.slope,
            this.floor
        );
    }

    /**
     * Reads parameters.
     *
     * @param input Serialised parameters
     * @return The parameters
     */
    static Bm25Params read(final WeightBytes input) {
        return new Bm25Params(
            input.number(), input.number(), input.number(), input.number(),
            input.number()
        );
    }

    /**
     * Parses the five leading parameters of a parameter string.
     *
     * @param input Parameter cursor, not at the end
     * @return The parameters
     */
    static Bm25Params parse(final WeightParams input) {
        return new Bm25Params(
            input.number("Parameter 1 (k1) is invalid"),
            input.optional(0.0, "Parameter 2 (k2) is invalid"),
            input.optional(1.0, "Parameter 3 (k3) is invalid"),
            input.optional(0.5, "Parameter 4 (b) is invalid"),
            input.optional(0.5, "Parameter 5 (min_normlen) is invalid")
        );
    }

    /**
     * Reciprocal of the average length, zero when lengths are unused.
     *
     * @param coll Collection statistics
     * @return Length factor
     */
    double lengths(final CollectionStats coll) {
        double factor = 0.0;
        if (this.correction != 0.0
            || this.slope != 0.0 && this.termsat != 0.0) {
            final double average = coll.average();
            if (average != 0.0) {
                factor = 1.0 / average;
            }
        }
        return factor;
    }

    /**
     * Scales a term weight by the within-query frequency.
     *
     * @param weight Term weight
     * @param wqf Within-query frequency
     * @return Scaled weight
     */
    double query(final double weight, final double wqf) {
        double result = weight;
        if (this.querysat != 0.0) {
            result = weight * ((this.querysat + 1.0) * wqf / (this.querysat + wqf));
        }
        return result;
    }

    /**
     * Saturation term of the denominator.
     *
     * @param normal Normalised document length
     * @return Term saturation scaled by the normalised length
     */
    double denominator(final double normal) {
        return this.termsat * (normal * this.slope + (1.0 - this.slope));
    }

    /**
     * Normalised document length.
     *
     * @param len Document length
     * @param factor Reciprocal of the average length
     * @return Normalised length, at least the floor
     */
    double normal(final double len, final double factor) {
        return WeightMath.larger(len * factor, this.floor);
    }
}
