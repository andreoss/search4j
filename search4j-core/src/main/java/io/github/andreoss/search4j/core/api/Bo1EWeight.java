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
 * Bo1 expansion weight: the Bo1 model from Amati's DfR framework.
 *
 * <p>The weight is based on the probability of a term occurring in the
 * matching documents compared to its expected occurrence by chance.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.UncommentedEmptyConstructor",
    "PMD.UncommentedEmptyMethodBody"
})
public final class Bo1EWeight implements ExpandWeight {

    /**
     * Number of documents matching the expanded query.
     */
    private int matches;

    /**
     * Creates a Bo1 expansion weight.
     */
    public Bo1EWeight() {
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
        final double total = stats.docs().value();
        final double expected = dfr * (dfr + 1.0) / total;
        if (tfr > 0.0 && dfr > 0.0 && total > 0.0 && expected > 0.0) {
            weight = Math.log(tfr / expected) / Math.log(2.0);
        }
        return weight;
    }

    @Override
    public ExpandWeight copy() {
        final Bo1EWeight copy = new Bo1EWeight();
        copy.matches = this.matches;
        return copy;
    }

    @Override
    public byte[] serialise() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(1);
            Varints.encode(out, this.matches);
        } catch (final IOException ex) {
            throw new AssertionError(ex);
        }
        return out.toByteArray();
    }

    @Override
    public String toString() {
        return String.format("Bo1EWeight()");
    }

    /**
     * Deserialises a Bo1 expansion weight from an input stream.
     *
     * @param source Stream positioned after the type byte
     * @return The weight
     * @throws IOException when the stream is malformed
     */
    static Bo1EWeight deserialise(final InputStream source) throws IOException {
        final Bo1EWeight weight = new Bo1EWeight();
        weight.matches = Varints.decode(source);
        return weight;
    }
}
