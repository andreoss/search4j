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
 * BM25+ weight scheme: BM25 with a constant added to prevent over-penalisation
 * of long documents.
 *
 * <p>BM25+ adds a constant delta to the BM25 score to ensure that terms
 * source long documents are not overly penalised.</p>
 *
 * @since 0.1.0
 */
public final class BM25PlusWeight implements Weight {

    /**
     * Term frequency saturation parameter.
     */
    private double sat;

    /**
     * Document length normalisation parameter.
     */
    private double norm;

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
     * Creates a BM25+ weight with default parameters (sat=1.2, norm=0.75, delta=1.0).
     */
    public BM25PlusWeight() {
        this(1.2, 0.75, 1.0);
    }

    /**
     * Creates a BM25+ weight with specified parameters.
     *
     * @param sat Term frequency saturation parameter
     * @param norm Document length normalisation parameter
     * @param delta Constant added to the score
     */
    public BM25PlusWeight(final double sat, final double norm, final double delta) {
        this.sat = sat;
        this.norm = norm;
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
            final double part = WeightNorms.bmNorm(
                wdf, doclen, new Bm25Norm(this.avglen, this.sat, this.norm)
            );
            weight = (idf * part + this.delta) * wqf.value();
        }
        return weight;
    }

    @Override
    public Weight copy() {
        final BM25PlusWeight copy = new BM25PlusWeight(this.sat, this.norm, this.delta);
        copy.avglen = this.avglen;
        copy.docs = this.docs;
        return copy;
    }

    @Override
    public byte[] serialise() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(4);
            Varints.encode(out, (long) (this.sat * 1000));
            Varints.encode(out, (long) (this.norm * 1000));
            Varints.encode(out, (long) (this.delta * 1000));
        } catch (final IOException ex) {
            throw new AssertionError(ex);
        }
        return out.toByteArray();
    }

    @Override
    public String toString() {
        return String.format(
            "BM25PlusWeight(k1=%.3f, b=%.3f, delta=%.3f)",
            this.sat, this.norm, this.delta
        );
    }

    /**
     * Deserialises a BM25+ weight from an input stream.
     *
     * @param source Stream positioned after the type byte
     * @return The weight
     * @throws IOException when the stream is malformed
     */
    static BM25PlusWeight deserialise(final InputStream source) throws IOException {
        final BM25PlusWeight weight = new BM25PlusWeight();
        weight.sat = Varints.decode(source) / 1000.0;
        weight.norm = Varints.decode(source) / 1000.0;
        weight.delta = Varints.decode(source) / 1000.0;
        return weight;
    }
}
