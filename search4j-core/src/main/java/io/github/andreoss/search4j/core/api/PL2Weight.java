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
import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.ser.Varints;
import io.github.andreoss.search4j.core.types.TermCount;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * PL2 weight scheme: Divergence from Randomness with Laplace after-effect.
 *
 * <p>PL2 uses the Poisson approximation to the binomial distribution
 * with a Laplace after-effect correction for term frequency.</p>
 *
 * @since 0.1.0
 */
public final class PL2Weight implements Weight {

    /**
     * After-effect parameter.
     */
    private double coef;

    /**
     * Average document length of the database.
     */
    private TermCount avglen;

    /**
     * Number of documents in the database.
     */
    private TermCount docs;

    /**
     * Creates a PL2 weight with default parameter (coef=1.0).
     */
    public PL2Weight() {
        this(1.0);
    }

    /**
     * Creates a PL2 weight with specified parameter.
     *
     * @param coef Laplace after-effect parameter
     */
    public PL2Weight(final double coef) {
        this.coef = coef;
    }

    @Override
    public void init(
        final ShardStatistics database, final Query query, final int terms
    ) {
        this.docs = database.docCount();
        if (this.docs.value() > 0) {
            this.avglen = new TermCount((int) (database.totalLength().value() / this.docs.value()));
        } else {
            this.avglen = TermCount.ZERO;
        }
    }

    @Override
    public double weight(
        final String term, final TermStats stats, final DocStats doc
    ) {
        final TermCount wqf = stats.wqf();
        final TermCount wdf = stats.wdf();
        final TermCount doclen = doc.length();
        double weight = 0.0;
        if (this.avglen != null && this.avglen.value() > 0
            && this.docs != null && this.docs.value() > 0) {
            final double idf = WeightNorms.idf(this.docs, wdf);
            final double part = WeightNorms.wdfPl(wdf, doclen, this.avglen);
            final double tfn = part / (part + this.coef) * (this.coef / (this.coef + 1.0));
            weight = idf * tfn * wqf.value();
        }
        return weight;
    }

    @Override
    public Weight copy() {
        final PL2Weight copy = new PL2Weight(this.coef);
        copy.avglen = this.avglen;
        copy.docs = this.docs;
        return copy;
    }

    @Override
    public byte[] serialise() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(5);
            Varints.encode(out, (long) (this.coef * 1000));
        } catch (final IOException ex) {
            throw new AssertionError(ex);
        }
        return out.toByteArray();
    }

    @Override
    public String toString() {
        return String.format("PL2Weight(c=%.3f)", this.coef);
    }

    /**
     * Deserialises a PL2 weight from an input stream.
     *
     * @param source Stream positioned after the type byte
     * @return The weight
     * @throws IOException when the stream is malformed
     */
    static PL2Weight deserialise(final InputStream source) throws IOException {
        final PL2Weight weight = new PL2Weight();
        weight.coef = Varints.decode(source) / 1000.0;
        return weight;
    }
}
