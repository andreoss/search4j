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
 * Prepared term and length weights of absolute discount smoothing.
 *
 * @param scale Scaled within-query frequency
 * @param mult Total length over discount and collection frequency
 * @param delta Discount subtracted from each frequency
 * @param query Length of the query
 * @param offset Length weight of the longest document
 * @param wdfmax Upper bound on the within-document frequency
 * @param docmin Lower bound on the document length
 * @since 0.1.0
 */
record DiscountPart(
    double scale, double mult, double delta, double query, double offset,
    int wdfmax, int docmin
) implements TermWeight, ExtraWeight {

    @Override
    public double sumPart(final TermCount wdf, final DocStats doc) {
        return this.scale * Math.log(
            1.0 + (wdf.value() - this.delta)
                / WeightMath.unsigned(doc.unique().value()) * this.mult
        );
    }

    @Override
    public double maxPart() {
        double ratio = (this.wdfmax - this.delta) * this.mult;
        if (Integer.compareUnsigned(this.docmin, this.wdfmax) > 0) {
            ratio = ratio
                * (Integer.divideUnsigned(this.docmin - 1, this.wdfmax) + 1);
        }
        return this.scale * Math.log(1.0 + ratio);
    }

    @Override
    public double sumExtra(final DocStats doc) {
        return this.offset + this.query * Math.log(
            WeightMath.unsigned(doc.unique().value()) / doc.length().value()
        );
    }

    @Override
    public double maxExtra() {
        return this.offset;
    }
}
