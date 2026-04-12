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
 * BM25 weighting from the probabilistic relevance framework.
 *
 * @since 0.1.0
 */
public final class BM25Weight implements Weight {

    /**
     * Name of the scheme.
     */
    private static final String NAME = "bm25";

    /**
     * Scheme parameters.
     */
    private final Bm25Params tuning;

    /**
     * Creates the scheme with the default parameters.
     */
    public BM25Weight() {
        this(Bm25Params.DEFAULT);
    }

    /**
     * Creates the scheme.
     *
     * @param tuning Scheme parameters
     */
    public BM25Weight(final Bm25Params tuning) {
        this.tuning = tuning;
    }

    @Override
    public String name() {
        return BM25Weight.NAME;
    }

    @Override
    public byte[] serialise() {
        return this.tuning.bytes();
    }

    @Override
    public Weight unserialise(final byte[] data) {
        final WeightBytes input = WeightBytes.of(BM25Weight.NAME, data);
        final Bm25Params read = Bm25Params.read(input);
        input.end();
        return new BM25Weight(read);
    }

    @Override
    public Weight create(final String params) {
        Weight result = new BM25Weight();
        if (!params.isEmpty()) {
            final WeightParams input = new WeightParams(BM25Weight.NAME, params);
            final Bm25Params parsed = Bm25Params.parse(input);
            input.end("Extra data after parameter 5");
            result = new BM25Weight(parsed);
        }
        return result;
    }

    @Override
    public TermWeight term(final WeightStats stats, final double factor) {
        final CollectionStats coll = stats.collection();
        final TermStats term = stats.term();
        final long freq = Integer.toUnsignedLong(term.freq().value());
        final long rset = Integer.toUnsignedLong(coll.rset().value());
        final long size = Integer.toUnsignedLong(coll.size().value());
        double ratio = (size - freq + 0.5) / (freq + 0.5);
        if (rset != 0L) {
            final long rel = Integer.toUnsignedLong(term.relFreq().value());
            final long missed = rset - rel;
            ratio = (rel + 0.5) * (size - missed - freq + 0.5)
                / ((missed + 0.5) * (freq - rel + 0.5));
        }
        if (ratio < 2.0) {
            ratio = ratio * 0.5 + 1.0;
        }
        return new Bm25Part(
            this.tuning,
            this.tuning.query(Math.log(ratio) * factor, term.wqf().value())
                * (this.tuning.termsat() + 1.0),
            1.0, 0.0, this.tuning.lengths(coll),
            term.wdfMax(), coll.bounds().docMin()
        );
    }

    @Override
    public ExtraWeight extra(final WeightStats stats) {
        return Bm25Extra.prepare(this.tuning, stats);
    }

    /**
     * Creates the traditional probabilistic scheme, a BM25 special case.
     *
     * @param params Saturation parameter, empty for one
     * @return The scheme
     */
    static Weight trad(final String params) {
        double sat = 1.0;
        if (!params.isEmpty()) {
            final WeightParams input = new WeightParams("trad", params);
            sat = input.number("Parameter is invalid");
            input.end("Extra data after parameter");
        }
        return new BM25Weight(new Bm25Params(sat, 0.0, 0.0, 1.0, 0.0));
    }
}
