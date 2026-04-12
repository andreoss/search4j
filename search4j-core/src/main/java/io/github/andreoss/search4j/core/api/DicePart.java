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
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Prepared Dice weight of a term.
 *
 * @param numerator Twice the scaled within-query frequency
 * @param query Length of the query
 * @param upper Upper bound on the weight
 * @since 0.1.0
 */
record DicePart(double numerator, int query, double upper)
    implements TermWeight {

    /**
     * Prepares the weight of a term.
     *
     * @param stats Collection and term statistics
     * @param factor Scale factor of the term
     * @return Term weight
     */
    static TermWeight prepare(final WeightStats stats, final double factor) {
        final CollectionStats coll = stats.collection();
        final double numerator = stats.term().wqf().value() * 2 * factor;
        final int query = coll.query().value();
        return new DicePart(
            numerator, query,
            numerator / WeightMath.unsigned(
                query + coll.bounds().uniqueMin().value()
            )
        );
    }

    @Override
    public double sumPart(final TermCount wdf, final DocStats doc) {
        return this.numerator
            / WeightMath.unsigned(this.query + doc.unique().value());
    }

    @Override
    public double maxPart() {
        return this.upper;
    }
}
