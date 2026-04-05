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
 * InL2 weight scheme: Inverse Document Frequency (L2 variant).
 *
 * <p>InL2 uses the standard IDF with the L2 after-effect normalisation.</p>
 *
 * @since 0.1.0
 */
public final class InL2Weight implements Weight {

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
     * Creates an InL2 weight with default parameter (coef=1.0).
     */
    public InL2Weight() {
        this(1.0);
    }

    /**
     * Creates an InL2 weight with specified parameter.
     *
     * @param coef After-effect parameter
     */
    public InL2Weight(final double coef) {
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
        final double value = wdf.value();
        final boolean sized = this.docs != null && this.docs.value() > 0;
        final boolean average = this.avglen != null && this.avglen.value() > 0;
        if (sized && average && value > 0.0) {
            final double avg = this.avglen.value();
            final double idf = Math.log(
                this.docs.value() / value
            ) / Math.log(2.0);
            final double denom = value
                + this.coef * (doclen.value() / avg);
            weight = idf * (value / denom) * wqf.value();
        }
        return weight;
    }

    @Override
    public Weight copy() {
        final InL2Weight copy = new InL2Weight(this.coef);
        copy.avglen = this.avglen;
        copy.docs = this.docs;
        return copy;
    }

    @Override
    public byte[] serialise() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(9);
            Varints.encode(out, (long) (this.coef * 1000));
        } catch (final IOException ex) {
            throw new AssertionError(ex);
        }
        return out.toByteArray();
    }

    @Override
    public String toString() {
        return String.format("InL2Weight(c=%.3f)", this.coef);
    }

    /**
     * Deserialises an InL2 weight from an input stream.
     *
     * @param source Stream positioned after the type byte
     * @return The weight
     * @throws IOException when the stream is malformed
     */
    static InL2Weight deserialise(final InputStream source) throws IOException {
        final InL2Weight weight = new InL2Weight();
        weight.coef = Varints.decode(source) / 1000.0;
        return weight;
    }
}
