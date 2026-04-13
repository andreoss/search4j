/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.matcher.ExtraWeight;

/**
 * Prepared document length correction of a BM25 scheme.
 *
 * @param params Scheme parameters
 * @param numerator Twice the correction weight times the query length
 * @param factor Reciprocal of the average length, zero when unused
 * @param docmin Lower bound on the document length
 * @since 0.1.0
 */
record Bm25Extra(Bm25Params params, double numerator, double factor, long docmin)
    implements ExtraWeight {

    /**
     * Prepares the correction.
     *
     * @param params Scheme parameters
     * @param stats Collection statistics
     * @return The correction
     */
    static ExtraWeight prepare(final Bm25Params params, final WeightStats stats) {
        final CollectionStats coll = stats.collection();
        return new Bm25Extra(
            params,
            2.0 * params.correction() * WeightMath.unsigned(coll.query().value()),
            params.lengths(coll), coll.bounds().docMin().value()
        );
    }

    @Override
    public double sumExtra(final DocStats doc) {
        return this.numerator
            / (1.0 + this.params.normal((double) doc.length().value(), this.factor));
    }

    @Override
    public double maxExtra() {
        double result = 0.0;
        if (this.params.correction() != 0.0) {
            result = this.numerator
                / (1.0 + this.params.normal((double) this.docmin, this.factor));
        }
        return result;
    }
}
