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
import io.github.andreoss.search4j.core.ser.Varints;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TotalLength;
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

    private double k1;
    private double b;
    private TermCount avgLength;
    private TermCount docCount;

    /**
     * Creates a BM25 weight with default parameters (k1=1.2, b=0.75).
     */
    public BM25Weight() {
        this(1.2, 0.75);
    }

    /**
     * Creates a BM25 weight with specified parameters.
     *
     * @param k1 Term frequency saturation parameter
     * @param b Document length normalisation parameter
     */
    public BM25Weight(final double k1, final double b) {
        this.k1 = k1;
        this.b = b;
    }

    @Override
    public void init(
        final ShardStatistics database, final Query query, final int termCount
    ) {
        this.docCount = database.docCount();
        final TotalLength totalLen = database.totalLength();
        if (this.docCount.value() > 0) {
            this.avgLength = new TermCount((int) (totalLen.value() / this.docCount.value()));
        } else {
            this.avgLength = TermCount.ZERO;
        }
    }

    @Override
    public double weight(
        final String term, final TermCount wqf, final TermCount wdf,
        final TermCount docLength, final TermCount uniqueTerms
    ) {
        if (this.avgLength == null || this.avgLength.value() <= 0) {
            return 0.0;
        }
        if (this.docCount == null || this.docCount.value() <= 0) {
            return 0.0;
        }
        final double idf = WeightNorms.idf(this.docCount, wdf);
        final double wdfNorm = WeightNorms.wdfNormalised(wdf, docLength, this.avgLength, this.k1, this.b);
        return idf * wdfNorm * wqf.value();
    }

    @Override
    public double weight(
        final String term, final TermCount wqf, final TermCount wdf,
        final TermCount docLength, final TermCount uniqueTerms,
        final java.util.List<TermCount> positions
    ) {
        return this.weight(term, wqf, wdf, docLength, uniqueTerms);
    }

    @Override
    public Weight copy() {
        final BM25Weight copy = new BM25Weight(this.k1, this.b);
        copy.avgLength = this.avgLength;
        copy.docCount = this.docCount;
        return copy;
    }

    @Override
    public byte[] serialise() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(3); // BM25 type
            Varints.encode(out, (long) (this.k1 * 1000));
            Varints.encode(out, (long) (this.b * 1000));
        } catch (final IOException e) {
            throw new AssertionError(e);
        }
        return out.toByteArray();
    }

    /**
     * Deserialises a BM25 weight from an input stream.
     *
     * @param in Stream positioned after the type byte
     * @return The weight
     * @throws IOException when the stream is malformed
     */
    static BM25Weight deserialise(final InputStream in) throws IOException {
        final BM25Weight weight = new BM25Weight();
        weight.k1 = Varints.decode(in) / 1000.0;
        weight.b = Varints.decode(in) / 1000.0;
        return weight;
    }

    @Override
    public String toString() {
        return String.format("BM25Weight(k1=%.3f, b=%.3f)", this.k1, this.b);
    }
}
