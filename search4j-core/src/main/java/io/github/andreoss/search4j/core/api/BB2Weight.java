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
 * BB2 weight scheme: Bose-Einstein statistics B2 variant.
 *
 * <p>BB2 uses the Bose-Einstein statistics for the after-effect correction,
 * which models term occurrences as indistinguishable particles.</p>
 *
 * @since 0.1.0
 */
public final class BB2Weight implements Weight {

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
     * Creates a BB2 weight with the default parameter.
     */
    public BB2Weight() {
        this(1.0);
    }

    /**
     * Creates a BB2 weight with the given after-effect parameter.
     *
     * @param coef After-effect parameter
     */
    public BB2Weight(final double coef) {
        this.coef = coef;
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
        double weight = 0.0;
        if (this.avglen != null && this.avglen.value() > 0
            && this.docs != null && this.docs.value() > 0) {
            final double idf = Math.log(
                (this.docs.value() + 1.0) / (wdf.value() + 1.0)
            ) / Math.log(2.0);
            final double value = wdf.value();
            weight = idf * (value / (value + this.coef)) * wqf.value();
        }
        return weight;
    }

    @Override
    public Weight copy() {
        final BB2Weight copy = new BB2Weight(this.coef);
        copy.avglen = this.avglen;
        copy.docs = this.docs;
        return copy;
    }

    @Override
    public byte[] serialise() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(12);
            Varints.encode(out, (long) (this.coef * 1000));
        } catch (final IOException ex) {
            throw new AssertionError(ex);
        }
        return out.toByteArray();
    }

    @Override
    public String toString() {
        return String.format("BB2Weight(c=%.3f)", this.coef);
    }

    /**
     * Deserialises a BB2 weight from an input stream.
     *
     * @param source Stream positioned after the type byte
     * @return The weight
     * @throws IOException when the stream is malformed
     */
    static BB2Weight deserialise(final InputStream source) throws IOException {
        final BB2Weight weight = new BB2Weight();
        weight.coef = Varints.decode(source) / 1000.0;
        return weight;
    }
}
