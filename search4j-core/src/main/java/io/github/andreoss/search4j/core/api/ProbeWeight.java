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
 * Probe expansion weight: the probe model from Amati's DfR framework.
 *
 * <p>The weight is based on the information gain of a term in the
 * matching documents.</p>
 *
 * @since 0.1.0
 */
public final class ProbeWeight implements ExpandWeight {

    private int matchingDocs;

    /**
     * Creates a Probe expansion weight.
     */
    public ProbeWeight() {}

    @Override
    public void init(
        final ShardStatistics database, final Query query, final int termCount
    ) {}

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
        // Probe weight: log2((docFreq + 0.5) / (termFreq - docFreq + 0.5))
        final double tf = termFreq.value();
        final double df = docFreq.value();
        final double numerator = df + 0.5;
        final double denominator = tf - df + 0.5;
        if (denominator <= 0.0) {
            return 0.0;
        }
        return Math.log(numerator / denominator) / Math.log(2.0);
    }

    @Override
    public ExpandWeight copy() {
        final ProbeWeight copy = new ProbeWeight();
        copy.matchingDocs = this.matchingDocs;
        return copy;
    }

    @Override
    public byte[] serialise() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(2);
            Varints.encode(out, this.matchingDocs);
        } catch (final IOException e) {
            throw new AssertionError(e);
        }
        return out.toByteArray();
    }

    /**
     * Deserialises a Probe expansion weight from an input stream.
     *
     * @param in Stream positioned after the type byte
     * @return The weight
     * @throws IOException when the stream is malformed
     */
    static ProbeWeight deserialise(final InputStream in) throws IOException {
        final ProbeWeight weight = new ProbeWeight();
        weight.matchingDocs = Varints.decode(in);
        return weight;
    }

    @Override
    public String toString() {
        return String.format("ProbeWeight()");
    }
}
