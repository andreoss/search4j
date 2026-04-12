/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.backend.list.TermWeight;
import io.github.andreoss.search4j.core.matcher.ExtraWeight;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Prepared term and length weights of two-stage smoothing.
 *
 * @param stage Smoothing parameters and query length
 * @param scale Scaled within-query frequency
 * @param mult Weight of the document model times total length over
 *  collection frequency
 * @param offset Length weight of the longest document, negated
 * @param wdfmax Upper bound on the within-document frequency
 * @param docmin Lower bound on the document length
 * @since 0.1.0
 */
record TwoStagePart(
    TwoStage stage, double scale, double mult, double offset, int wdfmax,
    int docmin
) implements TermWeight, ExtraWeight {

    @Override
    public double sumPart(final TermCount wdf, final DocStats doc) {
        return this.scale * Math.log(
            1.0 + wdf.value() / this.stage.smoothed(doc.length().value())
                * this.mult
        );
    }

    @Override
    public double maxPart() {
        return this.scale * Math.log(
            1.0 + this.wdfmax
                / this.stage.smoothed(Math.max(this.docmin, this.wdfmax))
                * this.mult
        );
    }

    @Override
    public double sumExtra(final DocStats doc) {
        return this.offset + this.stage.length(doc.length().value());
    }

    @Override
    public double maxExtra() {
        return this.offset + this.stage.length(this.docmin);
    }
}
