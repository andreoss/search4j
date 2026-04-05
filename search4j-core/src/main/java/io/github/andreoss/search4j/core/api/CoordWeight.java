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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Coordinate weight: weight is the fraction of query terms present.
 *
 * <p>The weight of a document is the number of query terms it contains
 * divided by the total number of query terms.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.UncommentedEmptyConstructor")
public final class CoordWeight implements Weight {

    /**
     * Number of terms in the query.
     */
    private int terms;

    /**
     * Creates a Coordinate weight.
     */
    public CoordWeight() {
    }

    @Override
    public void init(
        final ShardStatistics database, final Query query, final int count
    ) {
        this.terms = count;
    }

    @Override
    public double weight(
        final String term, final TermStats stats, final DocStats doc
    ) {
        double weight = 0.0;
        if (this.terms > 0) {
            weight = 1.0 / this.terms;
        }
        return weight;
    }

    @Override
    public Weight copy() {
        final CoordWeight copy = new CoordWeight();
        copy.terms = this.terms;
        return copy;
    }

    @Override
    public byte[] serialise() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(2);
            Varints.encode(out, this.terms);
        } catch (final IOException ex) {
            throw new AssertionError(ex);
        }
        return out.toByteArray();
    }

    @Override
    public String toString() {
        return String.format("CoordWeight(%d)", this.terms);
    }

    /**
     * Deserialises a Coordinate weight from an input stream.
     *
     * @param source Stream positioned after the type byte
     * @return The weight
     * @throws IOException when the stream is malformed
     */
    static CoordWeight deserialise(final InputStream source)
        throws IOException {
        final CoordWeight weight = new CoordWeight();
        weight.terms = Varints.decode(source);
        return weight;
    }
}
