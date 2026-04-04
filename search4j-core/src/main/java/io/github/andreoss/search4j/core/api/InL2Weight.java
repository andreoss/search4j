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
 * InL2 weight scheme: Inverse Document Frequency (L2 variant).
 *
 * <p>InL2 uses the standard IDF with the L2 after-effect normalisation.</p>
 *
 * @since 0.1.0
 */
public final class InL2Weight implements Weight {

    private double c;
    private TermCount avgLength;
    private TermCount docCount;

    /**
     * Creates an InL2 weight with default parameter (c=1.0).
     */
    public InL2Weight() {
        this(1.0);
    }

    /**
     * Creates an InL2 weight with specified parameter.
     *
     * @param c After-effect parameter
     */
    public InL2Weight(final double c) {
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
        // InL2 IDF: log2(N / nt)
        final double idf = Math.log(this.docCount.value() / wdf.value()) / Math.log(2.0);
        // L2 normalisation: wdf / (wdf + c * (1 - b + b * docLength / avgLength))
        // For InL2, b = 1 (full length normalisation)
        final double wdfVal = wdf.value();
        final double dl = docLength.value();
        final double avgl = this.avgLength.value();
        if (wdfVal <= 0.0 || avgl <= 0.0) {
            return 0.0;
        }
        final double denom = wdfVal + this.c * (dl / avgl);
        final double tfn = denom > 0.0 ? wdfVal / denom : 0.0;
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
        final InL2Weight copy = new InL2Weight(this.c);
        copy.avgLength = this.avgLength;
        copy.docCount = this.docCount;
        return copy;
    }

    @Override
    public byte[] serialise() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(9); // InL2 type
            Varints.encode(out, (long) (this.c * 1000));
        } catch (final IOException e) {
            throw new AssertionError(e);
        }
        return out.toByteArray();
    }

    /**
     * Deserialises an InL2 weight from an input stream.
     *
     * @param in Stream positioned after the type byte
     * @return The weight
     * @throws IOException when the stream is malformed
     */
    static InL2Weight deserialise(final InputStream in) throws IOException {
        final InL2Weight weight = new InL2Weight();
        weight.c = Varints.decode(in) / 1000.0;
        return weight;
    }

    @Override
    public String toString() {
        return String.format("InL2Weight(c=%.3f)", this.c);
    }
}
