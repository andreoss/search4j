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
 * LM weight scheme: Language Model (Dirichlet smoothing).
 *
 * <p>LM uses a language modeling approach with Dirichlet smoothing
 * for term weighting.</p>
 *
 * @since 0.1.0
 */
public final class LMWeight implements Weight {

    private double mu;
    private TermCount avgLength;
    private TermCount docCount;

    /**
     * Creates an LM weight with default parameter (mu=2500).
     */
    public LMWeight() {
        this(2500.0);
    }

    /**
     * Creates an LM weight with specified parameter.
     *
     * @param mu Dirichlet smoothing parameter
     */
    public LMWeight(final double mu) {
        this.mu = mu;
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
        if (this.docCount == null || this.docCount.value() <= 0) {
            return 0.0;
        }
        // LM with Dirichlet smoothing: (wdf + mu * cf / N) / (docLength + mu)
        // For simplicity, we use the collection frequency from term frequencies
        final double cf = wdf.value(); // placeholder - would need collection freq
        final double tf = wdf.value();
        final double dl = docLength.value();
        final double N = this.docCount.value();
        
        if (dl + this.mu <= 0.0) {
            return 0.0;
        }
        final double prob = (tf + this.mu * cf / N) / (dl + this.mu);
        final double idf = Math.log(N / cf) / Math.log(2.0);
        
        if (prob <= 0.0) {
            return 0.0;
        }
        return idf * prob * wqf.value();
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
        final LMWeight copy = new LMWeight(this.mu);
        copy.avgLength = this.avgLength;
        copy.docCount = this.docCount;
        return copy;
    }

    @Override
    public byte[] serialise() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(13); // LM type
            Varints.encode(out, (long) (this.mu * 1000));
        } catch (final IOException e) {
            throw new AssertionError(e);
        }
        return out.toByteArray();
    }

    /**
     * Deserialises an LM weight from an input stream.
     *
     * @param in Stream positioned after the type byte
     * @return The weight
     * @throws IOException when the stream is malformed
     */
    static LMWeight deserialise(final InputStream in) throws IOException {
        final LMWeight weight = new LMWeight();
        weight.mu = Varints.decode(in) / 1000.0;
        return weight;
    }

    @Override
    public String toString() {
        return String.format("LMWeight(mu=%.3f)", this.mu);
    }
}
