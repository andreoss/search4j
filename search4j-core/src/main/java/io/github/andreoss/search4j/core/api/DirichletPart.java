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
 * Prepared term and length weights of Dirichlet smoothing.
 *
 * @param scale Scaled within-query frequency
 * @param mult Total length over collection frequency and pseudo-length
 * @param mass Smoothing pseudo-length
 * @param offset Length weight of the longest document, negated
 * @param query Length of the query
 * @param wdfmax Upper bound on the within-document frequency
 * @param docmin Lower bound on the document length
 * @since 0.1.0
 */
record DirichletPart(
    double scale, double mult, double mass, double offset, double query,
    long wdfmax, long docmin
) implements TermWeight, ExtraWeight {

    @Override
    public double sumPart(final TermCount wdf, final DocStats doc) {
        return this.scale * Math.log(1.0 + wdf.value() * this.mult);
    }

    @Override
    public double maxPart() {
        return this.scale * Math.log(1.0 + this.wdfmax * this.mult);
    }

    @Override
    public double sumExtra(final DocStats doc) {
        return this.offset
            - this.query * Math.log(doc.length().value() + this.mass);
    }

    @Override
    public double maxExtra() {
        return this.offset - this.query * Math.log(this.docmin + this.mass);
    }
}
