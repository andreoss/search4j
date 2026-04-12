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
 * BM25+ weighting: BM25 with a lower bound on the saturated frequency.
 *
 * @since 0.1.0
 */
public final class BM25PlusWeight implements Weight {

    /**
     * Name of the scheme.
     */
    private static final String NAME = "bm25+";

    /**
     * Scheme parameters.
     */
    private final Bm25Params tuning;

    /**
     * Addend of the saturated frequency.
     */
    private final Clamped delta;

    /**
     * Creates the scheme with the default parameters.
     */
    public BM25PlusWeight() {
        this(Bm25Params.DEFAULT, 1.0);
    }

    /**
     * Creates the scheme; a negative addend is raised to zero.
     *
     * @param tuning Scheme parameters
     * @param delta Addend of the saturated frequency
     */
    public BM25PlusWeight(final Bm25Params tuning, final double delta) {
        this.tuning = tuning;
        this.delta = new Clamped(delta);
    }

    @Override
    public String name() {
        return BM25PlusWeight.NAME;
    }

    @Override
    public byte[] serialise() {
        return WeightBytes.doubles(
            this.tuning.termsat(), this.tuning.correction(),
            this.tuning.querysat(), this.tuning.slope(), this.tuning.floor(),
            this.delta.value()
        );
    }

    @Override
    public Weight unserialise(final byte[] data) {
        final WeightBytes input = WeightBytes.of(BM25PlusWeight.NAME, data);
        final Bm25Params read = Bm25Params.read(input);
        final double add = input.number();
        input.end();
        return new BM25PlusWeight(read, add);
    }

    @Override
    public Weight create(final String params) {
        Weight result = new BM25PlusWeight();
        if (!params.isEmpty()) {
            final WeightParams input = new WeightParams(BM25PlusWeight.NAME, params);
            final Bm25Params parsed = Bm25Params.parse(input);
            final double add = input.optional(1.0, "Parameter 6 (delta) is invalid");
            input.end("Extra data after parameter 6");
            result = new BM25PlusWeight(parsed, add);
        }
        return result;
    }

    @Override
    public TermWeight term(final WeightStats stats, final double factor) {
        final CollectionStats coll = stats.collection();
        final TermStats term = stats.term();
        double weight = 0.0;
        if (term.freq().value() != 0) {
            weight = this.tuning.query(
                Math.log(
                    WeightMath.unsigned(coll.size().value() + 1)
                        / WeightMath.unsigned(term.freq().value())
                ) * factor,
                term.wqf().value()
            );
        }
        return new Bm25Part(
            this.tuning, weight, this.tuning.termsat() + 1.0, this.delta.value(),
            this.tuning.lengths(coll), term.wdfMax(), coll.bounds().docMin()
        );
    }

    @Override
    public ExtraWeight extra(final WeightStats stats) {
        return Bm25Extra.prepare(this.tuning, stats);
    }
}
