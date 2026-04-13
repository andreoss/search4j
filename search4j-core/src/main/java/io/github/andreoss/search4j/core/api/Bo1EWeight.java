/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.backend.ShardStatistics;

/**
 * Bo1 expansion weight: the Bo1 model from Amati's DfR framework.
 *
 * <p>The weight grows with the occurrences of a term in the relevant
 * documents, scaled by the mean frequency of the term in the database.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.UncommentedEmptyConstructor")
public final class Bo1EWeight implements ExpandWeight {

    /**
     * Type byte of the serialised form.
     */
    static final int TYPE = 1;

    /**
     * Statistics of the database expanded over, set by initialisation.
     */
    private ShardStatistics database;

    /**
     * Creates a Bo1 expansion weight.
     */
    public Bo1EWeight() {
    }

    @Override
    public void init(
        final ShardStatistics stats, final Query query, final int terms
    ) {
        this.database = stats;
    }

    @Override
    public double weight(final String term, final ExpandStats stats) {
        final double mean = this.database.frequencies(term).collection().value()
            / (double) this.database.docCount().value();
        return stats.freq().value() * Bo1EWeight.binary((1.0 + mean) / mean)
            + Bo1EWeight.binary(1.0 + mean);
    }

    @Override
    public ExpandWeight copy() {
        return new Bo1EWeight();
    }

    @Override
    public byte[] serialise() {
        return new byte[] {(byte) Bo1EWeight.TYPE};
    }

    @Override
    public String toString() {
        return "Bo1EWeight()";
    }

    private static double binary(final double value) {
        return Math.log(value) / Math.log(2.0);
    }
}
