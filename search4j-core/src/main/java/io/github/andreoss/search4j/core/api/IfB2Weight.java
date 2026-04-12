/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.list.TermWeight;

/**
 * IfB2 weighting from the divergence from randomness framework:
 * inverse term frequency, Bernoulli after-effect, second normalisation.
 *
 * @since 0.1.0
 */
public final class IfB2Weight implements Weight {

    /**
     * Name of the scheme.
     */
    private static final String NAME = "ifb2";

    /**
     * Document length normalisation parameter, above zero.
     */
    private final Positive coef;

    /**
     * Creates the scheme with the default parameter one.
     */
    public IfB2Weight() {
        this(1.0);
    }

    /**
     * Creates the scheme.
     *
     * @param coef Document length normalisation parameter, above zero
     */
    public IfB2Weight(final double coef) {
        this.coef = new Positive("c", coef);
    }

    @Override
    public String name() {
        return IfB2Weight.NAME;
    }

    @Override
    public byte[] serialise() {
        return WeightBytes.doubles(this.coef.value());
    }

    @Override
    public Weight unserialise(final byte[] data) {
        return new IfB2Weight(WeightBytes.single(IfB2Weight.NAME, data));
    }

    @Override
    public Weight create(final String params) {
        return new IfB2Weight(WeightParams.single(IfB2Weight.NAME, params, 1.0));
    }

    @Override
    public TermWeight term(final WeightStats stats, final double factor) {
        final TermWeight part;
        if (stats.term().wdfMax().value() == 0) {
            part = new FixedPart(0.0);
        } else {
            final double upper = EffectPart.highest(stats, this.coef.value());
            final double size = WeightMath.unsigned(stats.collection().size().value());
            final double coll = WeightMath.unsigned(stats.term().collFreq().value());
            final double effect = (coll + 1.0)
                / WeightMath.unsigned(stats.term().freq().value());
            final double scale = stats.term().wqf().value()
                * WeightMath.logTwo((size + 1.0) / (coll + 0.5)) * factor;
            part = new EffectPart(
                this.coef.value() * stats.collection().average(), effect, scale,
                scale * (upper * effect / (upper + 1.0)) * factor
            );
        }
        return part;
    }
}
