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
     * Normalisations.
     */
    private final TfidfNorms norms;

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
        this.norms = norms;
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
        all[head.length] = (byte) this.norms.wdf().code();
        all[head.length + 1] = (byte) this.norms.idf().code();
        all[head.length + 2] = (byte) this.norms.wt().code();
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
        return new TfidfWeight(
            new TfidfNorms(
                WdfNorm.byCode(input.code()), IdfNorm.byCode(input.code()),
                WtNorm.byCode(input.code())
            ),
            first, second
        );
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
            this.norms.wdf(),
            new Pivot(
                stats.collection().average(), this.slope.value(),
                this.delta.value()
            ),
            this.norms.idf().apply(stats), stats.term().wqf().value() * factor,
            stats.term().wdfMax(), stats.collection().bounds().docMin()
        );
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
