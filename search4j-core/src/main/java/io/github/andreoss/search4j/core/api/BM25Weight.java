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
 * BM25 weight scheme.
 *
 * <p>The BM25 weight uses the probabilistic relevance framework with
 * term frequency saturation and document length normalisation.</p>
 *
 * @since 0.1.0
 */
public final class BM25Weight implements Weight {

    /**
     * Term frequency saturation parameter.
     */
    private double sat;

    /**
     * Document length normalisation parameter.
     */
    private double norm;

    /**
     * Average document length of the database.
     */
    private TermCount avglen;

    /**
     * Number of documents in the database.
     */
    private TermCount docs;

    /**
     * Creates a BM25 weight with the default parameters.
     */
    public BM25Weight() {
        this(1.2, 0.75);
    }

    /**
     * Creates a BM25 weight with the given parameters.
     *
     * @param sat Term frequency saturation parameter
     * @param norm Document length normalisation parameter
     */
    public BM25Weight(final double sat, final double norm) {
        this.sat = sat;
        this.norm = norm;
    }

    @Override
    public void init(
        final ShardStatistics database, final Query query, final int terms
    ) {
        this.docs = database.docCount();
        if (this.docs.value() > 0) {
            this.avglen = new TermCount(
                (int) (database.totalLength().value() / this.docs.value())
            );
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
            weight = idf * part * wqf.value();
        }
        return weight;
    }

    @Override
    public Weight copy() {
        final BM25Weight copy = new BM25Weight(this.sat, this.norm);
        copy.avglen = this.avglen;
        copy.docs = this.docs;
        return copy;
    }

    @Override
    public byte[] serialise() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(3);
            Varints.encode(out, (long) (this.sat * 1000));
            Varints.encode(out, (long) (this.norm * 1000));
        } catch (final IOException ex) {
            throw new AssertionError(ex);
        }
        return out.toByteArray();
    }

    @Override
    public String toString() {
        return String.format(
            "BM25Weight(k1=%.3f, b=%.3f)", this.sat, this.norm
        );
    }

    /**
     * Deserialises a BM25 weight from an input stream.
     *
     * @param source Stream positioned after the type byte
     * @return The weight
     * @throws IOException when the stream is malformed
     */
    static BM25Weight deserialise(final InputStream source) throws IOException {
        final BM25Weight weight = new BM25Weight();
        weight.sat = Varints.decode(source) / 1000.0;
        weight.norm = Varints.decode(source) / 1000.0;
        return weight;
    }
}
