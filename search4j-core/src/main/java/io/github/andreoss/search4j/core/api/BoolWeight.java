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
import io.github.andreoss.search4j.core.types.TermCount;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Boolean weight: every matching document has weight one.
 *
 * <p>This weight scheme ignores term frequencies and document lengths;
 * it is useful for boolean retrieval and testing.</p>
 *
 * @since 0.1.0
 */
public final class BoolWeight implements Weight {

    /**
     * Creates a Boolean weight.
     */
    public BoolWeight() {}

    @Override
    public void init(
        final ShardStatistics database, final Query query, final int termCount
    ) {}

    @Override
    public double weight(
        final String term, final TermCount wqf, final TermCount wdf,
        final TermCount docLength, final TermCount uniqueTerms
    ) {
        return 1.0;
    }

    @Override
    public double weight(
        final String term, final TermCount wqf, final TermCount wdf,
        final TermCount docLength, final TermCount uniqueTerms,
        final java.util.List<TermCount> positions
    ) {
        return 1.0;
    }

    @Override
    public Weight copy() {
        return new BoolWeight();
    }

    @Override
    public byte[] serialise() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(1);
        return out.toByteArray();
    }

    /**
     * Deserialises a Boolean weight from an input stream.
     *
     * @param in Stream positioned after the type byte
     * @return The weight
     * @throws IOException when the stream is malformed
     */
    static BoolWeight deserialise(final java.io.InputStream in)
        throws IOException {
        return new BoolWeight();
    }

    @Override
    public String toString() {
        return "BoolWeight()";
    }
}
