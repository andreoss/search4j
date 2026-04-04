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
 * DLH weight scheme: Divergence from Randomness with Hellinger after-effect.
 *
 * <p>DLH uses the Hellinger distance for the after-effect correction,
 * which provides a symmetric measure between distributions.</p>
 *
 * @since 0.1.0
 */
public final class DLHWeight implements Weight {

    private double c;
    private TermCount avgLength;
    private TermCount docCount;

    /**
     * Creates a DLH weight with default parameter (c=1.0).
     */
    public DLHWeight() {
        this(1.0);
    }

    /**
     * Creates a DLH weight with specified parameter.
     *
     * @param c After-effect parameter
     */
    public DLHWeight(final double c) {
        this.c = c;
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
        final double wdfNorm = WeightNorms.wdfDLH(wdf, docLength, this.avgLength);
        // DLH normalisation: wdfNorm / (wdfNorm + c)
        final double tfn = wdfNorm / (wdfNorm + this.c);
        return idf * tfn * wqf.value();
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
        final DLHWeight copy = new DLHWeight(this.c);
        copy.avgLength = this.avgLength;
        copy.docCount = this.docCount;
        return copy;
    }

    @Override
    public byte[] serialise() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(11); // DLH type
            Varints.encode(out, (long) (this.c * 1000));
        } catch (final IOException e) {
            throw new AssertionError(e);
        }
        return out.toByteArray();
    }

    /**
     * Deserialises a DLH weight from an input stream.
     *
     * @param in Stream positioned after the type byte
     * @return The weight
     * @throws IOException when the stream is malformed
     */
    static DLHWeight deserialise(final InputStream in) throws IOException {
        final DLHWeight weight = new DLHWeight();
        weight.c = Varints.decode(in) / 1000.0;
        return weight;
    }

    @Override
    public String toString() {
        return String.format("DLHWeight(c=%.3f)", this.c);
    }
}
