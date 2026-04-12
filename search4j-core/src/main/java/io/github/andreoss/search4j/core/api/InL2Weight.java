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
 * InL2 weighting from the divergence from randomness framework:
 * inverse document frequency, Laplace after-effect, second normalisation.
 *
 * @since 0.1.0
 */
public final class InL2Weight implements Weight {

    /**
     * Name of the scheme.
     */
    private static final String NAME = "inl2";

    /**
     * Document length normalisation parameter, above zero.
     */
    private final Positive coef;

    /**
     * Creates the scheme with the default parameter one.
     */
    public InL2Weight() {
        this(1.0);
    }

    /**
     * Creates the scheme.
     *
     * @param coef Document length normalisation parameter, above zero
     */
    public InL2Weight(final double coef) {
        this.coef = new Positive("c", coef);
    }

    @Override
    public String name() {
        return InL2Weight.NAME;
    }

    @Override
    public byte[] serialise() {
        return WeightBytes.doubles(this.coef.value());
    }

    @Override
    public Weight unserialise(final byte[] data) {
        return new InL2Weight(WeightBytes.single(InL2Weight.NAME, data));
    }

    @Override
    public Weight create(final String params) {
        return new InL2Weight(WeightParams.single(InL2Weight.NAME, params, 1.0));
    }

    @Override
    public TermWeight term(final WeightStats stats, final double factor) {
        final TermWeight part;
        if (stats.term().wdfMax().value() == 0) {
            part = new FixedPart(0.0);
        } else {
            final double upper = EffectPart.highest(stats, this.coef.value());
            final double idf = WeightMath.logTwo(
                (WeightMath.unsigned(stats.collection().size().value()) + 1.0)
                    / (WeightMath.unsigned(stats.term().freq().value()) + 0.5)
            );
            final double scale = stats.term().wqf().value() * idf * factor;
            part = new EffectPart(
                this.coef.value() * stats.collection().average(), 1.0, scale,
                scale * (upper / (upper + 1.0)) * factor
            );
        }
        return part;
    }
}
