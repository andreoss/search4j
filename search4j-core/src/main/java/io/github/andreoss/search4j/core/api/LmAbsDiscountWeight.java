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
 * Language model weighting with absolute discount smoothing.
 *
 * @since 0.1.0
 */
public final class LmAbsDiscountWeight implements Weight {

    /**
     * Name of the scheme.
     */
    private static final String NAME = "lmabsdiscount";

    /**
     * Discount subtracted from each frequency.
     */
    private final double delta;

    /**
     * Creates the scheme with the default discount 0.7.
     */
    public LmAbsDiscountWeight() {
        this(0.7);
    }

    /**
     * Creates the scheme.
     *
     * @param delta Discount subtracted from each frequency
     */
    public LmAbsDiscountWeight(final double delta) {
        this.delta = delta;
    }

    @Override
    public String name() {
        return LmAbsDiscountWeight.NAME;
    }

    @Override
    public byte[] serialise() {
        return WeightBytes.doubles(this.delta);
    }

    @Override
    public Weight unserialise(final byte[] data) {
        return new LmAbsDiscountWeight(
            WeightBytes.single(LmAbsDiscountWeight.NAME, data)
        );
    }

    @Override
    public Weight create(final String params) {
        final WeightParams input = new WeightParams(LmAbsDiscountWeight.NAME, params);
        final double value = input.optional(0.7, "Parameter delta is invalid");
        input.end("Extra data after parameter");
        return new LmAbsDiscountWeight(value);
    }

    @Override
    public TermWeight term(final WeightStats stats, final double factor) {
        return this.prepare(stats, factor);
    }

    @Override
    public ExtraWeight extra(final WeightStats stats) {
        return this.prepare(stats, 0.0);
    }

    private DiscountPart prepare(final WeightStats stats, final double factor) {
        final CollectionStats coll = stats.collection();
        final double query = WeightMath.unsigned(coll.query().value());
        final long freq = stats.term().collFreq().value();
        double scale = 0.0;
        double mult = 0.0;
        if (freq != 0) {
            scale = factor * stats.term().wqf().value();
            mult = coll.total().asDouble() / (this.delta * WeightMath.unsigned(freq));
        }
        return new DiscountPart(
            scale, mult, this.delta, query,
            query * Math.log(WeightMath.unsigned(coll.bounds().docMax().value())),
            stats.term().wdfMax().value(), coll.bounds().docMin().value()
        );
    }
}
