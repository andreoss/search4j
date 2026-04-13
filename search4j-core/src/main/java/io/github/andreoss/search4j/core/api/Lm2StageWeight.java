/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.list.TermWeight;
import io.github.andreoss.search4j.core.matcher.ExtraWeight;

/**
 * Language model weighting with two-stage smoothing: Dirichlet smoothing
 * interpolated with the collection model.
 *
 * @since 0.1.0
 */
public final class Lm2StageWeight implements Weight {

    /**
     * Name of the scheme.
     */
    private static final String NAME = "lm2stage";

    /**
     * Interpolation weight of the collection model.
     */
    private final double lambda;

    /**
     * Smoothing pseudo-length.
     */
    private final double mass;

    /**
     * Creates the scheme with the default parameters 0.7 and 2000.
     */
    public Lm2StageWeight() {
        this(0.7, 2000.0);
    }

    /**
     * Creates the scheme.
     *
     * @param lambda Interpolation weight of the collection model
     * @param mass Smoothing pseudo-length
     */
    public Lm2StageWeight(final double lambda, final double mass) {
        this.lambda = lambda;
        this.mass = mass;
    }

    @Override
    public String name() {
        return Lm2StageWeight.NAME;
    }

    @Override
    public byte[] serialise() {
        return WeightBytes.doubles(this.lambda, this.mass);
    }

    @Override
    public Weight unserialise(final byte[] data) {
        final WeightBytes input = WeightBytes.of(Lm2StageWeight.NAME, data);
        final double first = input.number();
        final double second = input.number();
        input.end();
        return new Lm2StageWeight(first, second);
    }

    @Override
    public Weight create(final String params) {
        final WeightParams input = new WeightParams(Lm2StageWeight.NAME, params);
        final double first = input.optional(0.7, "Parameter lambda is invalid");
        final double second = input.optional(2000.0, "Parameter mu is invalid");
        input.end("Extra data after parameters");
        return new Lm2StageWeight(first, second);
    }

    @Override
    public TermWeight term(final WeightStats stats, final double factor) {
        return this.prepare(stats, factor);
    }

    @Override
    public ExtraWeight extra(final WeightStats stats) {
        return this.prepare(stats, 0.0);
    }

    private TwoStagePart prepare(final WeightStats stats, final double factor) {
        final CollectionStats coll = stats.collection();
        final double query = WeightMath.unsigned(coll.query().value());
        final double longest = WeightMath.unsigned(coll.bounds().docMax().value());
        final long freq = stats.term().collFreq().value();
        double scale = 0.0;
        double mult = 0.0;
        if (freq != 0) {
            scale = factor * stats.term().wqf().value();
            mult = (1.0 - this.lambda) * coll.total().value()
                / WeightMath.unsigned(freq);
        }
        return new TwoStagePart(
            new TwoStage(this.lambda, this.mass, query), scale, mult,
            -Math.log((this.lambda * longest + this.mass) / (longest + this.mass))
                * query,
            stats.term().wdfMax().value(), coll.bounds().docMin().value()
        );
    }
}
