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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Probe expansion weight: the probe model of the divergence from
 * randomness framework.
 *
 * <p>The weight is based on the information gain of a term in the matching
 * documents.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.UncommentedEmptyConstructor",
    "PMD.UncommentedEmptyMethodBody"
})
public final class ProbeWeight implements ExpandWeight {

    /**
     * Number of documents matching the expanded query.
     */
    private int matches;

    /**
     * Creates a Probe expansion weight.
     */
    public ProbeWeight() {
    }

    @Override
    public void init(
        final ShardStatistics database, final Query query, final int terms
    ) {
    }

    /**
     * Sets the number of matching documents.
     *
     * @param docs Number of matching documents
     */
    public void setMatchingDocs(final int docs) {
        this.matches = docs;
    }

    @Override
    public double weight(final String term, final ExpandStats stats) {
        double weight = 0.0;
        final double tfr = stats.freq().value();
        final double dfr = stats.dfreq().value();
        final double den = tfr - dfr + 0.5;
        if (tfr > 0.0 && dfr > 0.0 && stats.docs().value() > 0 && den > 0.0) {
            weight = Math.log((dfr + 0.5) / den) / Math.log(2.0);
        }
        return weight;
    }

    @Override
    public ExpandWeight copy() {
        final ProbeWeight copy = new ProbeWeight();
        copy.matches = this.matches;
        return copy;
    }

    @Override
    public byte[] serialise() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(2);
            Varints.encode(out, this.matches);
        } catch (final IOException ex) {
            throw new AssertionError(ex);
        }
        return out.toByteArray();
    }

    @Override
    public String toString() {
        return String.format("ProbeWeight()");
    }

    /**
     * Deserialises a Probe expansion weight from an input stream.
     *
     * @param source Stream positioned after the type byte
     * @return The weight
     * @throws IOException when the stream is malformed
     */
    static ProbeWeight deserialise(final InputStream source)
        throws IOException {
        final ProbeWeight weight = new ProbeWeight();
        weight.matches = Varints.decode(source);
        return weight;
    }
}
