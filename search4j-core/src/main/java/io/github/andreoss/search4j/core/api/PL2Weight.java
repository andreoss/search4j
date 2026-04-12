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
 * PL2 weighting from the divergence from randomness framework:
 * Poisson model, Laplace after-effect, second normalisation.
 *
 * @since 0.1.0
 */
public final class PL2Weight implements Weight {

    /**
     * Name of the scheme.
     */
    private static final String NAME = "pl2";

    /**
     * Document length normalisation parameter, above zero.
     */
    private final Positive coef;

    /**
     * Creates the scheme with the default parameter one.
     */
    public PL2Weight() {
        this(1.0);
    }

    /**
     * Creates the scheme.
     *
     * @param coef Document length normalisation parameter, above zero
     */
    public PL2Weight(final double coef) {
        this.coef = new Positive("c", coef);
    }

    @Override
    public String name() {
        return PL2Weight.NAME;
    }

    @Override
    public byte[] serialise() {
        return WeightBytes.doubles(this.coef.value());
    }

    @Override
    public Weight unserialise(final byte[] data) {
        return new PL2Weight(WeightBytes.single(PL2Weight.NAME, data));
    }

    @Override
    public Weight create(final String params) {
        return new PL2Weight(WeightParams.single(PL2Weight.NAME, params, 1.0));
    }

    @Override
    public TermWeight term(final WeightStats stats, final double factor) {
        return Pl2Part.prepare(stats, factor, this.coef.value());
    }
}
