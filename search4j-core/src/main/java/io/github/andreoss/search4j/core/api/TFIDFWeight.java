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
 * TF-IDF weight scheme: Term Frequency - Inverse Document Frequency.
 *
 * <p>TF-IDF weight is the product of term frequency and inverse document
 * frequency.</p>
 *
 * @since 0.1.0
 */
public final class TFIDFWeight implements Weight {

    private TermCount docCount;

    /**
     * Creates a TF-IDF weight.
     */
    public TFIDFWeight() {}

    @Override
    public void init(
        final ShardStatistics database, final Query query, final int termCount
    ) {
        this.docCount = database.docCount();
    }

    @Override
    public double weight(
        final String term, final TermCount wqf, final TermCount wdf,
        final TermCount docLength, final TermCount uniqueTerms
    ) {
        if (this.docCount == null || this.docCount.value() <= 0) {
            return 0.0;
        }
        // TF-IDF: tf * log(N / df)
        final double tf = wdf.value();
        final double df = wdf.value(); // placeholder - would need doc freq
        final double N = this.docCount.value();
        
        if (df <= 0.0) {
            return 0.0;
        }
        final double idf = Math.log(N / df) / Math.log(2.0);
        return idf * tf * wqf.value();
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
        final TFIDFWeight copy = new TFIDFWeight();
        copy.docCount = this.docCount;
        return copy;
    }

    @Override
    public byte[] serialise() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(14); // TF-IDF type
        return out.toByteArray();
    }

    /**
     * Deserialises a TF-IDF weight from an input stream.
     *
     * @param in Stream positioned after the type byte
     * @return The weight
     * @throws IOException when the stream is malformed
     */
    static TFIDFWeight deserialise(final InputStream in) throws IOException {
        return new TFIDFWeight();
    }

    @Override
    public String toString() {
        return "TFIDFWeight()";
    }
}
