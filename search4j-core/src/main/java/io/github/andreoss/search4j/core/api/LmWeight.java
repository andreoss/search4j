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
 * LM weight scheme with Dirichlet smoothing.
 *
 * <p>The language model score mixes the document term frequency with the
 * collection rate, the mixing weight being the smoothing parameter.</p>
 *
 * @since 0.1.0
 */
public final class LmWeight implements Weight {

    /**
     * Dirichlet smoothing parameter.
     */
    private double smooth;

    /**
     * Average document length of the database.
     */
    private TermCount avglen;

    /**
     * Number of documents in the database.
     */
    private TermCount docs;

    /**
     * Creates an LM weight with the default smoothing parameter.
     */
    public LmWeight() {
        this(2500.0);
    }

    /**
     * Creates an LM weight with the given smoothing parameter.
     *
     * @param smooth Dirichlet smoothing parameter
     */
    public LmWeight(final double smooth) {
        this.smooth = smooth;
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
        double total = 0.0;
        if (this.docs != null) {
            total = this.docs.value();
        }
        final double freq = wdf.value();
        final double denom = doclen.value() + this.smooth;
        if (total > 0.0 && freq > 0.0 && denom > 0.0) {
            final double prob = (freq + this.smooth * freq / total) / denom;
            weight = Math.log(total / freq) / Math.log(2.0)
                * prob * wqf.value();
        }
        return weight;
    }

    @Override
    public Weight copy() {
        final LmWeight copy = new LmWeight(this.smooth);
        copy.avglen = this.avglen;
        copy.docs = this.docs;
        return copy;
    }

    @Override
    public byte[] serialise() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(13);
            Varints.encode(out, (long) (this.smooth * 1000));
        } catch (final IOException ex) {
            throw new AssertionError(ex);
        }
        return out.toByteArray();
    }

    @Override
    public String toString() {
        return String.format("LmWeight(smooth=%.3f)", this.smooth);
    }

    /**
     * Deserialises an LM weight from an input stream.
     *
     * @param source Stream positioned after the type byte
     * @return The weight
     * @throws IOException when the stream is malformed
     */
    static LmWeight deserialise(final InputStream source) throws IOException {
        final LmWeight weight = new LmWeight();
        weight.smooth = Varints.decode(source) / 1000.0;
        return weight;
    }
}
