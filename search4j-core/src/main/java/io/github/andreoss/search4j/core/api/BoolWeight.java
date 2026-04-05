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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Boolean weight: every matching document has weight one.
 *
 * <p>This weight scheme ignores term frequencies and document lengths;
 * it is useful for boolean retrieval and testing.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.UncommentedEmptyConstructor",
    "PMD.UncommentedEmptyMethodBody"
})
public final class BoolWeight implements Weight {

    /**
     * Creates a Boolean weight.
     */
    public BoolWeight() {
    }

    @Override
    public void init(
        final ShardStatistics database, final Query query, final int terms
    ) {
    }

    @Override
    public double weight(
        final String term, final TermStats stats, final DocStats doc
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

    @Override
    public String toString() {
        return "BoolWeight()";
    }

    /**
     * Deserialises a Boolean weight from an input stream.
     *
     * @param source Stream positioned after the type byte
     * @return The weight
     * @throws IOException when the stream is malformed
     */
    static BoolWeight deserialise(final InputStream source) throws IOException {
        return new BoolWeight();
    }
}
