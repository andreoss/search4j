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
 * PL2+ weight scheme: PL2 with parameter for term frequency saturation.
 *
 * <p>PL2+ adds a parameter to control the saturation of the term frequency
 * normalisation, providing more flexibility than standard PL2.</p>
 *
 * @since 0.1.0
 */
public final class PL2PlusWeight implements Weight {

    /**
     * After-effect parameter.
     */
    private double coef;

    /**
     * Constant added to the score.
     */
    private double delta;

    /**
     * Average document length of the database.
     */
    private TermCount avglen;

    /**
     * Number of documents in the database.
     */
    private TermCount docs;

    /**
     * Creates a PL2+ weight with default parameters (coef=1.0, delta=1.0).
     */
    public PL2PlusWeight() {
        this(1.0, 1.0);
    }

    /**
     * Creates a PL2+ weight with specified parameters.
     *
     * @param coef Laplace after-effect parameter
     * @param delta Saturation parameter
     */
    public PL2PlusWeight(final double coef, final double delta) {
        this.coef = coef;
        this.delta = delta;
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
            final double tfn = part / (part + this.coef) * (this.coef / (this.coef + this.delta));
            weight = idf * tfn * wqf.value();
        }
        return weight;
    }

    @Override
    public Weight copy() {
        final PL2PlusWeight copy = new PL2PlusWeight(this.coef, this.delta);
        copy.avglen = this.avglen;
        copy.docs = this.docs;
        return copy;
    }

    @Override
    public byte[] serialise() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(6);
            Varints.encode(out, (long) (this.coef * 1000));
            Varints.encode(out, (long) (this.delta * 1000));
        } catch (final IOException ex) {
            throw new AssertionError(ex);
        }
        return out.toByteArray();
    }

    @Override
    public String toString() {
        return String.format("PL2PlusWeight(c=%.3f, delta=%.3f)", this.coef, this.delta);
    }

    /**
     * Deserialises a PL2+ weight from an input stream.
     *
     * @param source Stream positioned after the type byte
     * @return The weight
     * @throws IOException when the stream is malformed
     */
    static PL2PlusWeight deserialise(final InputStream source) throws IOException {
        final PL2PlusWeight weight = new PL2PlusWeight();
        weight.coef = Varints.decode(source) / 1000.0;
        weight.delta = Varints.decode(source) / 1000.0;
        return weight;
    }
}
