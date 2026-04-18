/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.list.TermWeight;
import io.github.andreoss.search4j.core.error.SerialisationError;
import java.util.Optional;

/**
 * Tf-idf weighting with selectable normalisations.
 *
 * @since 0.1.0
 */
public final class TfidfWeight implements Weight {

    /**
     * Name of the scheme.
     */
    private static final String NAME = "tfidf";

    /**
     * Serialised codes of the frequency, document frequency and weight
     * normalisations.
     */
    private final int[] codes;

    /**
     * Slope of the pivoted normalisation, above zero.
     */
    private final Positive slope;

    /**
     * Addend of the pivoted normalisation, above zero.
     */
    private final Positive delta;

    /**
     * Creates the scheme with the default normalisations.
     */
    public TfidfWeight() {
        this(TfidfNorms.DEFAULT);
    }

    /**
     * Creates the scheme from the three-letter form of its normalisations.
     *
     * @param letters One letter per normalisation, such as {@code ntn}
     */
    public TfidfWeight(final String letters) {
        this(TfidfNorms.parse(letters));
    }

    /**
     * Creates the scheme with the default pivot parameters 0.2 and one.
     *
     * @param norms Normalisations
     */
    public TfidfWeight(final TfidfNorms norms) {
        this(norms, 0.2, 1.0);
    }

    /**
     * Creates the scheme.
     *
     * @param norms Normalisations
     * @param slope Slope of the pivoted normalisation, above zero
     * @param delta Addend of the pivoted normalisation, above zero
     */
    public TfidfWeight(final TfidfNorms norms, final double slope, final double delta) {
        this(slope, delta, norms.wdf().code(), norms.idf().code(), norms.wt().code());
    }

    private TfidfWeight(final double slope, final double delta, final int... codes) {
        this.codes = codes.clone();
        this.slope = new Positive("slope", slope);
        this.delta = new Positive("delta", delta);
    }

    @Override
    public String name() {
        return TfidfWeight.NAME;
    }

    @Override
    public byte[] serialise() {
        final byte[] head = WeightBytes.doubles(this.slope.value(), this.delta.value());
        final byte[] all = new byte[head.length + 3];
        System.arraycopy(head, 0, all, 0, head.length);
        for (int idx = 0; idx < this.codes.length; idx = idx + 1) {
            all[head.length + idx] = (byte) this.codes[idx];
        }
        return all;
    }

    @Override
    public Weight unserialise(final byte[] data) {
        final WeightBytes input = WeightBytes.of(TfidfWeight.NAME, data);
        final double first = input.number();
        final double second = input.number();
        if (input.left() != 3) {
            throw new SerialisationError("Incorrect data in tfidf parameters");
        }
        return new TfidfWeight(first, second, input.code(), input.code(), input.code());
    }

    @Override
    public Weight create(final String params) {
        Weight result = new TfidfWeight();
        if (!params.isEmpty()) {
            final WeightParams input = new WeightParams(TfidfWeight.NAME, params);
            final String first = input.name();
            final Optional<WdfNorm> wdf = WdfNorm.byWord(first);
            if (wdf.isPresent()) {
                result = new TfidfWeight(
                    new TfidfNorms(wdf.get(), TfidfWeight.rest(input), WtNorm.NONE)
                );
            } else if (first.length() == 3 && input.done()) {
                result = new TfidfWeight(first);
            } else {
                throw input.error("Parameter 1 (wdf_normalisation) is invalid");
            }
        }
        return result;
    }

    @Override
    public TermWeight term(final WeightStats stats, final double factor) {
        return new TfidfPart(
            WdfNorm.byCode(this.codes[0]),
            new Pivot(
                stats.collection().average(), this.slope.value(),
                this.delta.value()
            ),
            IdfNorm.byCode(this.codes[1], stats), stats.term().wqf().value() * factor,
            stats.term().wdfMax(), stats.collection().bounds().docMin(),
            TfidfWeight.lengthy(this.codes)
        );
    }

    private static boolean lengthy(final int... codes) {
        return codes[0] == WdfNorm.PIVOTED.code() || codes[0] == WdfNorm.LOG_AVERAGE.code()
            || codes[0] == WdfNorm.AUG_AVERAGE.code() || codes[1] == IdfNorm.PIVOTED.code();
    }

    private static IdfNorm rest(final WeightParams input) {
        final IdfNorm idf = IdfNorm.byWord(input.name()).orElseThrow(
            () -> input.error("Parameter 2 (idf_normalisation) is invalid")
        );
        if (!"NONE".equals(input.name())) {
            throw input.error("Parameter 3 (wt_normalisation) is invalid");
        }
        input.end("Extra data after parameter 3");
        return idf;
    }
}
