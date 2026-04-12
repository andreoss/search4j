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
 * BB2 weighting from the divergence from randomness framework:
 * Bose-Einstein model, Bernoulli after-effect, second normalisation.
 *
 * @since 0.1.0
 */
public final class BB2Weight implements Weight {

    /**
     * Name of the scheme.
     */
    private static final String NAME = "bb2";

    /**
     * Document length normalisation parameter, above zero.
     */
    private final Positive coef;

    /**
     * Creates the scheme with the default parameter one.
     */
    public BB2Weight() {
        this(1.0);
    }

    /**
     * Creates the scheme.
     *
     * @param coef Document length normalisation parameter, above zero
     */
    public BB2Weight(final double coef) {
        this.coef = new Positive("c", coef);
    }

    @Override
    public String name() {
        return BB2Weight.NAME;
    }

    @Override
    public byte[] serialise() {
        return WeightBytes.doubles(this.coef.value());
    }

    @Override
    public Weight unserialise(final byte[] data) {
        return new BB2Weight(WeightBytes.single(BB2Weight.NAME, data));
    }

    @Override
    public Weight create(final String params) {
        return new BB2Weight(WeightParams.single(BB2Weight.NAME, params, 1.0));
    }

    @Override
    public TermWeight term(final WeightStats stats, final double factor) {
        return Bb2Part.prepare(stats, factor, this.coef.value());
    }
}
