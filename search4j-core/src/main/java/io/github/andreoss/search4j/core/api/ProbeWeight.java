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
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Probabilistic expansion weight: the relevance weight of a term from its
 * frequency in the relevant documents and in the database, multiplied by
 * the shares of the relevant documents indexed by it.
 *
 * <p>A document contributes a share that grows with the frequency of the
 * term and shrinks with the length of the document, tuned by a parameter
 * k; with k zero every document contributes one.</p>
 *
 * @since 0.1.0
 */
public final class ProbeWeight implements ExpandWeight {

    /**
     * Type byte of the serialised form.
     */
    static final int TYPE = 2;

    /**
     * Parameter k of the document shares.
     */
    private final double param;

    /**
     * Statistics of the database expanded over, set by initialisation.
     */
    private ShardStatistics database;

    /**
     * Creates a probabilistic expansion weight with k one.
     */
    public ProbeWeight() {
        this(1.0);
    }

    /**
     * Creates a probabilistic expansion weight.
     *
     * @param param Parameter k of the document shares, zero or more
     */
    public ProbeWeight(final double param) {
        this.param = param;
    }

    @Override
    public void init(
        final ShardStatistics stats, final Query query, final int terms
    ) {
        this.database = stats;
    }

    @Override
    public double weight(final String term, final ExpandStats stats) {
        final double relevant = stats.dfreq().value();
        final double termfreq = this.database.frequencies(term).termFrequency().value();
        final double without = stats.docs().value() - relevant;
        return stats.multiplier() * Math.log(
            (relevant + 0.5)
                * (this.database.docCount().value() - termfreq - without + 0.5)
                / ((termfreq - relevant + 0.5) * (without + 0.5))
        );
    }

    @Override
    public double share(
        final TermCount wdf, final TermCount length, final double average
    ) {
        return (this.param + 1.0) * wdf.value()
            / (this.param * length.value() / average + wdf.value());
    }

    @Override
    public ExpandWeight copy() {
        return new ProbeWeight(this.param);
    }

    @Override
    public byte[] serialise() {
        final byte[] value = WeightBytes.doubles(this.param);
        final byte[] out = new byte[value.length + 1];
        out[0] = (byte) ProbeWeight.TYPE;
        System.arraycopy(value, 0, out, 1, value.length);
        return out;
    }

    @Override
    public String toString() {
        return String.format("ProbeWeight(%s)", this.param);
    }
}
