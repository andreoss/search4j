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
import io.github.andreoss.search4j.core.types.TermCount;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Dice coefficient weight scheme.
 *
 * <p>The weight is twice the term frequency over the sum of the term
 * frequency and the document frequency.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.UncommentedEmptyConstructor")
public final class DiceWeight implements Weight {

    /**
     * Number of documents in the database.
     */
    private TermCount docs;

    /**
     * Creates a Dice weight.
     */
    public DiceWeight() {
    }

    @Override
    public void init(
        final ShardStatistics database, final Query query, final int terms
    ) {
        this.docs = database.docCount();
    }

    @Override
    public double weight(
        final String term, final TermStats stats, final DocStats doc
    ) {
        final TermCount wqf = stats.wqf();
        final TermCount wdf = stats.wdf();
        double weight = 0.0;
        double total = 0.0;
        if (this.docs != null) {
            total = this.docs.value();
        }
        final double freq = wdf.value();
        if (total > 0.0 && freq > 0.0) {
            weight = 2.0 * freq / (freq + freq) * wqf.value();
        }
        return weight;
    }

    @Override
    public Weight copy() {
        final DiceWeight copy = new DiceWeight();
        copy.docs = this.docs;
        return copy;
    }

    @Override
    public byte[] serialise() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(15);
        return out.toByteArray();
    }

    @Override
    public String toString() {
        return "DiceWeight()";
    }

    /**
     * Deserialises a Dice weight from an input stream.
     *
     * @param source Stream positioned after the type byte
     * @return The weight
     * @throws IOException when the stream is malformed
     */
    static DiceWeight deserialise(final InputStream source) throws IOException {
        return new DiceWeight();
    }
}
