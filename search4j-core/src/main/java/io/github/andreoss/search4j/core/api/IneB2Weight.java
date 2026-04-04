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
 * IneB2 weight scheme: Inverse Expected Document Frequency (B2 variant).
 *
 * <p>IneB2 uses the inverse expected document frequency with the B2
 * after-effect normalisation.</p>
 *
 * @since 0.1.0
 */
public final class IneB2Weight implements Weight {

    private double c;
    private TermCount avgLength;
    private TermCount docCount;

    /**
     * Creates an IneB2 weight with default parameter (c=1.0).
     */
    public IneB2Weight() {
        this(1.0);
    }

    /**
     * Creates an IneB2 weight with specified parameter.
     *
     * @param c After-effect parameter
     */
    public IneB2Weight(final double c) {
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
        // IneB2 IDF: log2((N + 1) / (nt + 0.5))
        final double idf = Math.log((this.docCount.value() + 1.0) / (wdf.value() + 0.5)) / Math.log(2.0);
        final double wdfNorm = WeightNorms.wdfPL2(wdf, docLength, this.avgLength);
        final double tfn = wdfNorm / (wdfNorm + this.c) * (this.c / (this.c + 1.0));
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
        final IneB2Weight copy = new IneB2Weight(this.c);
        copy.avgLength = this.avgLength;
        copy.docCount = this.docCount;
        return copy;
    }

    @Override
    public byte[] serialise() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(7); // IneB2 type
            Varints.encode(out, (long) (this.c * 1000));
        } catch (final IOException e) {
            throw new AssertionError(e);
        }
        return out.toByteArray();
    }

    /**
     * Deserialises an IneB2 weight from an input stream.
     *
     * @param in Stream positioned after the type byte
     * @return The weight
     * @throws IOException when the stream is malformed
     */
    static IneB2Weight deserialise(final InputStream in) throws IOException {
        final IneB2Weight weight = new IneB2Weight();
        weight.c = Varints.decode(in) / 1000.0;
        return weight;
    }

    @Override
    public String toString() {
        return String.format("IneB2Weight(c=%.3f)", this.c);
    }
}
