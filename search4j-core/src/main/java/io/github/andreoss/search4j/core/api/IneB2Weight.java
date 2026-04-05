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
 * IneB2 weight scheme: Inverse Expected Document Frequency (B2 variant).
 *
 * <p>IneB2 uses the inverse expected document frequency with the B2
 * after-effect normalisation.</p>
 *
 * @since 0.1.0
 */
public final class IneB2Weight implements Weight {

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
     * Creates an IneB2 weight with default parameter (coef=1.0).
     */
    public IneB2Weight() {
        this(1.0);
    }

    /**
     * Creates an IneB2 weight with specified parameter.
     *
     * @param coef After-effect parameter
     */
    public IneB2Weight(final double coef) {
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
            final double idf = Math.log(
                (this.docs.value() + 1.0) / (wdf.value() + 0.5)
            ) / Math.log(2.0);
            final double part = WeightNorms.wdfPl(wdf, doclen, this.avglen);
            final double tfn = part / (part + this.coef) * (this.coef / (this.coef + 1.0));
            weight = idf * tfn * wqf.value();
        }
        return weight;
    }

    @Override
    public Weight copy() {
        final IneB2Weight copy = new IneB2Weight(this.coef);
        copy.avglen = this.avglen;
        copy.docs = this.docs;
        return copy;
    }

    @Override
    public byte[] serialise() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(7);
            Varints.encode(out, (long) (this.coef * 1000));
        } catch (final IOException ex) {
            throw new AssertionError(ex);
        }
        return out.toByteArray();
    }

    @Override
    public String toString() {
        return String.format("IneB2Weight(c=%.3f)", this.coef);
    }

    /**
     * Deserialises an IneB2 weight from an input stream.
     *
     * @param source Stream positioned after the type byte
     * @return The weight
     * @throws IOException when the stream is malformed
     */
    static IneB2Weight deserialise(final InputStream source) throws IOException {
        final IneB2Weight weight = new IneB2Weight();
        weight.coef = Varints.decode(source) / 1000.0;
        return weight;
    }
}
