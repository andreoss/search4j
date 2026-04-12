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
 * PL2+ weighting: PL2 with a lower bound on the normalised frequency.
 *
 * @since 0.1.0
 */
public final class PL2PlusWeight implements Weight {

    /**
     * Name of the scheme.
     */
    private static final String NAME = "pl2+";

    /**
     * Document length normalisation parameter, above zero.
     */
    private final Positive coef;

    /**
     * Lower bound frequency, above zero.
     */
    private final Positive delta;

    /**
     * Creates the scheme with the default parameters one and 0.8.
     */
    public PL2PlusWeight() {
        this(1.0, 0.8);
    }

    /**
     * Creates the scheme.
     *
     * @param coef Document length normalisation parameter, above zero
     * @param delta Lower bound frequency, above zero
     */
    public PL2PlusWeight(final double coef, final double delta) {
        this.coef = new Positive("c", coef);
        this.delta = new Positive("delta", delta);
    }

    @Override
    public String name() {
        return PL2PlusWeight.NAME;
    }

    @Override
    public byte[] serialise() {
        return WeightBytes.doubles(this.coef.value(), this.delta.value());
    }

    @Override
    public Weight unserialise(final byte[] data) {
        final WeightBytes input = WeightBytes.of(PL2PlusWeight.NAME, data);
        final double first = input.number();
        final double second = input.number();
        input.end();
        return new PL2PlusWeight(first, second);
    }

    @Override
    public Weight create(final String params) {
        Weight result = new PL2PlusWeight();
        if (!params.isEmpty()) {
            final WeightParams input = new WeightParams(PL2PlusWeight.NAME, params);
            final double first = input.number("Parameter 1 (c) is invalid");
            final double second = input.number("Parameter 2 (delta) is invalid");
            input.end("Extra data after parameter 2");
            result = new PL2PlusWeight(first, second);
        }
        return result;
    }

    @Override
    public TermWeight term(final WeightStats stats, final double factor) {
        return Pl2PlusPart.prepare(stats, factor, this.coef.value(), this.delta.value());
    }
}
