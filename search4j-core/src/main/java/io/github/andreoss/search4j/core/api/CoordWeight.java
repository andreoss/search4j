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
 * Coordinate weight: weight is the fraction of query terms present.
 *
 * <p>The weight of a document is the number of query terms it contains
 * divided by the total number of query terms.</p>
 *
 * @since 0.1.0
 */
public final class CoordWeight implements Weight {

    private int termCount;

    /**
     * Creates a Coordinate weight.
     */
    public CoordWeight() {}

    @Override
    public void init(
        final ShardStatistics database, final Query query, final int termCount
    ) {
        this.termCount = termCount;
    }

    @Override
    public double weight(
        final String term, final TermCount wqf, final TermCount wdf,
        final TermCount docLength, final TermCount uniqueTerms
    ) {
        return 1.0 / this.termCount;
    }

    @Override
    public double weight(
        final String term, final TermCount wqf, final TermCount wdf,
        final TermCount docLength, final TermCount uniqueTerms,
        final java.util.List<TermCount> positions
    ) {
        return 1.0 / this.termCount;
    }

    @Override
    public Weight copy() {
        final CoordWeight copy = new CoordWeight();
        copy.termCount = this.termCount;
        return copy;
    }

    @Override
    public byte[] serialise() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(2);
            Varints.encode(out, this.termCount);
        } catch (final IOException e) {
            throw new AssertionError(e);
        }
        return out.toByteArray();
    }

    /**
     * Deserialises a Coordinate weight from an input stream.
     *
     * @param in Stream positioned after the type byte
     * @return The weight
     * @throws IOException when the stream is malformed
     */
    static CoordWeight deserialise(final InputStream in) throws IOException {
        final CoordWeight weight = new CoordWeight();
        weight.termCount = Varints.decode(in);
        return weight;
    }

    @Override
    public String toString() {
        return String.format("CoordWeight(%d)", this.termCount);
    }
}
