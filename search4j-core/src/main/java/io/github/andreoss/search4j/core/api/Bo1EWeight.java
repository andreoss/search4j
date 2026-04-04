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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Bo1 expansion weight: the Bo1 model from Amati's DfR framework.
 *
 * <p>The weight is based on the probability of a term occurring in the
 * matching documents compared to its expected occurrence by chance.</p>
 *
 * @since 0.1.0
 */
public final class Bo1EWeight implements ExpandWeight {

    private int matchingDocs;

    /**
     * Creates a Bo1 expansion weight.
     */
    public Bo1EWeight() {}

    @Override
    public void init(
        final ShardStatistics database, final Query query, final int termCount
    ) {
        // Matching docs will be set during ESet generation
    }

    /**
     * Sets the number of matching documents.
     *
     * @param docs Number of matching documents
     */
    public void setMatchingDocs(final int docs) {
        this.matchingDocs = docs;
    }

    @Override
    public double weight(
        final String term, final TermCount wqf, final TermCount termFreq,
        final TermCount docFreq, final TermCount matchingDocs
    ) {
        if (termFreq.value() == 0 || docFreq.value() == 0 || matchingDocs.value() == 0) {
            return 0.0;
        }
        // Bo1 weight: log2((termFreq * matchingDocs) / (docFreq * (docFreq + 1)))
        final double tf = termFreq.value();
        final double df = docFreq.value();
        final double md = matchingDocs.value();
        final double expected = df * (df + 1) / md;
        if (expected <= 0.0) {
            return 0.0;
        }
        return Math.log(tf / expected) / Math.log(2.0);
    }

    @Override
    public ExpandWeight copy() {
        final Bo1EWeight copy = new Bo1EWeight();
        copy.matchingDocs = this.matchingDocs;
        return copy;
    }

    @Override
    public byte[] serialise() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(1);
            Varints.encode(out, this.matchingDocs);
        } catch (final IOException e) {
            throw new AssertionError(e);
        }
        return out.toByteArray();
    }

    /**
     * Deserialises a Bo1 expansion weight from an input stream.
     *
     * @param in Stream positioned after the type byte
     * @return The weight
     * @throws IOException when the stream is malformed
     */
    static Bo1EWeight deserialise(final InputStream in) throws IOException {
        final Bo1EWeight weight = new Bo1EWeight();
        weight.matchingDocs = Varints.decode(in);
        return weight;
    }

    @Override
    public String toString() {
        return String.format("Bo1EWeight()");
    }
}
