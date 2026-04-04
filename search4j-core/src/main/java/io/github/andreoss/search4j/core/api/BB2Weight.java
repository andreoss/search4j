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
 * BB2 weight scheme: Bose-Einstein statistics B2 variant.
 *
 * <p>BB2 uses the Bose-Einstein statistics for the after-effect correction,
 * which models term occurrences as indistinguishable particles.</p>
 *
 * @since 0.1.0
 */
public final class BB2Weight implements Weight {

    private double c;
    private TermCount avgLength;
    private TermCount docCount;

    /**
     * Creates a BB2 weight with default parameter (c=1.0).
     */
    public BB2Weight() {
        this(1.0);
    }

    /**
     * Creates a BB2 weight with specified parameter.
     *
     * @param c After-effect parameter
     */
    public BB2Weight(final double c) {
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
        // BB2 IDF: log2((N + 1) / (nt + 1))
        final double idf = Math.log((this.docCount.value() + 1.0) / (wdf.value() + 1.0)) / Math.log(2.0);
        // BB2 normalisation: wdf / (wdf + c)
        final double wdfVal = wdf.value();
        final double tfn = wdfVal / (wdfVal + this.c);
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
        final BB2Weight copy = new BB2Weight(this.c);
        copy.avgLength = this.avgLength;
        copy.docCount = this.docCount;
        return copy;
    }

    @Override
    public byte[] serialise() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(12); // BB2 type
            Varints.encode(out, (long) (this.c * 1000));
        } catch (final IOException e) {
            throw new AssertionError(e);
        }
        return out.toByteArray();
    }

    /**
     * Deserialises a BB2 weight from an input stream.
     *
     * @param in Stream positioned after the type byte
     * @return The weight
     * @throws IOException when the stream is malformed
     */
    static BB2Weight deserialise(final InputStream in) throws IOException {
        final BB2Weight weight = new BB2Weight();
        weight.c = Varints.decode(in) / 1000.0;
        return weight;
    }

    @Override
    public String toString() {
        return String.format("BB2Weight(c=%.3f)", this.c);
    }
}
