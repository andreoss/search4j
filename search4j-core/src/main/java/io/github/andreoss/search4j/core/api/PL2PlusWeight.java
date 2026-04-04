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
 * PL2+ weight scheme: PL2 with parameter for term frequency saturation.
 *
 * <p>PL2+ adds a parameter to control the saturation of the term frequency
 * normalisation, providing more flexibility than standard PL2.</p>
 *
 * @since 0.1.0
 */
public final class PL2PlusWeight implements Weight {

    private double c;
    private double delta;
    private TermCount avgLength;
    private TermCount docCount;

    /**
     * Creates a PL2+ weight with default parameters (c=1.0, delta=1.0).
     */
    public PL2PlusWeight() {
        this(1.0, 1.0);
    }

    /**
     * Creates a PL2+ weight with specified parameters.
     *
     * @param c Laplace after-effect parameter
     * @param delta Saturation parameter
     */
    public PL2PlusWeight(final double c, final double delta) {
        this.c = c;
        this.delta = delta;
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
        final double wdfNorm = WeightNorms.wdfPL2(wdf, docLength, this.avgLength);
        // PL2+ normalisation with delta
        final double tfn = wdfNorm / (wdfNorm + this.c) * (this.c / (this.c + this.delta));
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
        final PL2PlusWeight copy = new PL2PlusWeight(this.c, this.delta);
        copy.avgLength = this.avgLength;
        copy.docCount = this.docCount;
        return copy;
    }

    @Override
    public byte[] serialise() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(6); // PL2+ type
            Varints.encode(out, (long) (this.c * 1000));
            Varints.encode(out, (long) (this.delta * 1000));
        } catch (final IOException e) {
            throw new AssertionError(e);
        }
        return out.toByteArray();
    }

    /**
     * Deserialises a PL2+ weight from an input stream.
     *
     * @param in Stream positioned after the type byte
     * @return The weight
     * @throws IOException when the stream is malformed
     */
    static PL2PlusWeight deserialise(final InputStream in) throws IOException {
        final PL2PlusWeight weight = new PL2PlusWeight();
        weight.c = Varints.decode(in) / 1000.0;
        weight.delta = Varints.decode(in) / 1000.0;
        return weight;
    }

    @Override
    public String toString() {
        return String.format("PL2PlusWeight(c=%.3f, delta=%.3f)", this.c, this.delta);
    }
}
