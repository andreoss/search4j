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
 * Language model weighting with Dirichlet smoothing and a lower bound on
 * the frequency normalisation.
 *
 * @since 0.1.0
 */
public final class LmDirichletWeight implements Weight {

    /**
     * Name of the scheme.
     */
    private static final String NAME = "lmdirichlet";

    /**
     * Smoothing pseudo-length.
     */
    private final double mass;

    /**
     * Lower bound on the frequency normalisation, zero for none.
     */
    private final double delta;

    /**
     * Creates the scheme with the default parameters 2000 and 0.05.
     */
    public LmDirichletWeight() {
        this(2000.0, 0.05);
    }

    /**
     * Creates the scheme.
     *
     * @param mass Smoothing pseudo-length
     * @param delta Lower bound on the frequency normalisation, zero for none
     */
    public LmDirichletWeight(final double mass, final double delta) {
        this.mass = mass;
        this.delta = delta;
    }

    @Override
    public String name() {
        return LmDirichletWeight.NAME;
    }

    @Override
    public byte[] serialise() {
        return WeightBytes.doubles(this.mass, this.delta);
    }

    @Override
    public Weight unserialise(final byte[] data) {
        final WeightBytes input = WeightBytes.of(LmDirichletWeight.NAME, data);
        final double first = input.number();
        final double second = input.number();
        input.end();
        return new LmDirichletWeight(first, second);
    }

    @Override
    public Weight create(final String params) {
        final WeightParams input = new WeightParams(LmDirichletWeight.NAME, params);
        final double first = input.optional(2000.0, "Parameter mu is invalid");
        final double second = input.optional(0.05, "Parameter delta is invalid");
        input.end("Extra data after parameters");
        return new LmDirichletWeight(first, second);
    }

    @Override
    public TermWeight term(final WeightStats stats, final double factor) {
        return this.prepare(stats, factor);
    }

    @Override
    public ExtraWeight extra(final WeightStats stats) {
        return this.prepare(stats, 0.0);
    }

    private DirichletPart prepare(final WeightStats stats, final double factor) {
        final CollectionStats coll = stats.collection();
        final double query = WeightMath.unsigned(coll.query().value());
        double scale = factor * stats.term().wqf().value();
        double mult = 0.0;
        final int freq = stats.term().collFreq().value();
        if (scale != 0.0 && freq == 0) {
            scale = 0.0;
        } else if (scale != 0.0) {
            mult = coll.total().value() / (WeightMath.unsigned(freq) * this.mass);
            if (this.delta != 0.0) {
                scale = scale * Math.log(1.0 + this.delta * mult);
            }
        }
        return new DirichletPart(
            scale, mult, this.mass,
            query * Math.log(coll.bounds().docMax().value() + this.mass),
            query, stats.term().wdfMax().value(),
            coll.bounds().docMin().value()
        );
    }
}
