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
 * Language model weighting with Jelinek-Mercer smoothing.
 *
 * @since 0.1.0
 */
public final class LmjmWeight implements Weight {

    /**
     * Name of the scheme.
     */
    private static final String NAME = "lmjm";

    /**
     * Smoothing weight, chosen from the query length outside zero to one.
     */
    private final double lambda;

    /**
     * Creates the scheme choosing the smoothing from the query length.
     */
    public LmjmWeight() {
        this(0.0);
    }

    /**
     * Creates the scheme.
     *
     * @param lambda Smoothing weight, chosen from the query length outside
     *  zero to one
     */
    public LmjmWeight(final double lambda) {
        this.lambda = lambda;
    }

    @Override
    public String name() {
        return LmjmWeight.NAME;
    }

    @Override
    public byte[] serialise() {
        return WeightBytes.doubles(this.lambda);
    }

    @Override
    public Weight unserialise(final byte[] data) {
        return new LmjmWeight(WeightBytes.single(LmjmWeight.NAME, data));
    }

    @Override
    public Weight create(final String params) {
        final WeightParams input = new WeightParams(LmjmWeight.NAME, params);
        final double value = input.optional(0.0, "Parameter lambda is invalid");
        input.end("Extra data after parameter");
        return new LmjmWeight(value);
    }

    @Override
    public TermWeight term(final WeightStats stats, final double factor) {
        final long coll = stats.term().collFreq().value();
        double scale = 0.0;
        double mult = 0.0;
        if (coll != 0) {
            scale = factor * stats.term().wqf().value();
            final double smooth = this.smoothing(stats.collection().query().value());
            mult = (1.0 - smooth) * stats.collection().total().value()
                / (smooth * WeightMath.unsigned(coll));
        }
        return new Part(
            scale, mult, stats.term().wdfMax().value(),
            stats.collection().bounds().docMin().value()
        );
    }

    private double smoothing(final long query) {
        double smooth = this.lambda;
        if (smooth <= 0.0 || smooth >= 1.0) {
            if (query <= 2) {
                smooth = 0.1;
            } else if (query < 8) {
                smooth = (query - 1) * 0.1;
            } else {
                smooth = 0.7;
            }
        }
        return smooth;
    }

    /**
     * Prepared weight of a term.
     *
     * @param scale Scaled within-query frequency
     * @param mult Multiplier of the frequency over the length
     * @param wdfmax Upper bound on the within-document frequency
     * @param docmin Lower bound on the document length
     * @since 0.1.0
     */
    private record Part(double scale, double mult, long wdfmax, long docmin)
        implements TermWeight {

        @Override
        public double sumPart(final TermCount wdf, final DocStats doc) {
            return this.scale
                * Math.log(1.0 + this.mult * wdf.value() / doc.length().value());
        }

        @Override
        public double maxPart() {
            double ratio = this.mult;
            if (this.wdfmax < this.docmin) {
                ratio = ratio * ((double) this.wdfmax / this.docmin);
            }
            return this.scale * Math.log(1.0 + ratio);
        }
    }
}
