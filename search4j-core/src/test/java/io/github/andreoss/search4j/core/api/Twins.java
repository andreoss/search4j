/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API contracts.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.types.DatabaseAction;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A vidrio database and an in-memory database given the same documents.
 *
 * @since 0.1.0
 */
final class Twins implements AutoCloseable {

    /**
     * Vocabulary of the random documents.
     */
    static final List<String> WORDS = List.of(
        "alpha", "alps", "beta", "betray", "bb", "b", "gamma", "delta",
        "epsilon", "zeta", "eta", "theta", "iota", "kappa", "lambda", "mu",
        "nu", "xi", "omicron", "pi", "rho", "sigma", "tau", "upsilon", "phi",
        "chi", "psi", "omega", "émile", "日本語", "Zebra", "ZZtop", "a0",
        "a1", "a2", "longwordwithmanyletters", "x", "y", "z9", "q"
    );

    /**
     * Terms listed in snapshots: the vocabulary and an unknown term.
     */
    static final List<String> TERMS = Twins.terms();

    /**
     * The vidrio database.
     */
    private final WritableDatabase vidrio;

    /**
     * The in-memory database.
     */
    private final WritableDatabase memory;

    /**
     * Source of randomness.
     */
    private final Random random;

    /**
     * Creates the pair.
     *
     * @param vidrio The vidrio database
     * @param memory The in-memory database
     * @param seed Seed of the random documents
     */
    private Twins(
        final WritableDatabase vidrio, final WritableDatabase memory,
        final long seed
    ) {
        this.vidrio = vidrio;
        this.memory = memory;
        this.random = new Random(seed);
    }

    @Override
    public void close() {
        this.vidrio.close();
        this.memory.close();
    }

    /**
     * Creates a vidrio database in a directory next to an in-memory one.
     *
     * @param dir Database directory
     * @param seed Seed of the random documents
     * @return Pair
     */
    static Twins create(final Path dir, final long seed) {
        return new Twins(
            DatabaseFactory.openWritable(
                dir, DatabaseAction.CREATE.code(), 2048
            ),
            DatabaseFactory.inMemory(), seed
        );
    }

    /**
     * The vidrio database.
     *
     * @return Database
     */
    WritableDatabase vidrio() {
        return this.vidrio;
    }

    /**
     * The in-memory database.
     *
     * @return Database
     */
    WritableDatabase memory() {
        return this.memory;
    }

    /**
     * Adds the same random document to both.
     *
     * @return Identifier given by the vidrio database
     */
    DocId add() {
        final DocumentInternal doc = this.document();
        final DocId result = this.vidrio.addDocument(doc);
        this.memory.addDocument(doc);
        return result;
    }

    /**
     * Replaces a document in both with the same random document.
     *
     * @param doc Identifier
     */
    void replace(final DocId doc) {
        final DocumentInternal document = this.document();
        this.vidrio.replaceDocument(doc, document);
        this.memory.replaceDocument(doc, document);
    }

    /**
     * Deletes a document from both.
     *
     * @param doc Identifier
     */
    void delete(final DocId doc) {
        this.vidrio.deleteDocument(doc);
        this.memory.deleteDocument(doc);
    }

    /**
     * Sets metadata in both.
     *
     * @param key Key
     * @param value Value
     */
    void metadata(final String key, final String value) {
        this.vidrio.setMetadata(key, value);
        this.memory.setMetadata(key, value);
    }

    /**
     * A random document: terms with frequencies and positions, values and
     * data.
     *
     * @return Document
     */
    DocumentInternal document() {
        final DocumentInternal doc = new DocumentInternal();
        doc.setData(String.format("data %d", this.random.nextInt(1_000_000)));
        final int count = 1 + this.random.nextInt(12);
        for (int idx = 0; idx < count; ++idx) {
            final String term = Twins.WORDS.get(this.random.nextInt(Twins.WORDS.size()));
            final int wdf = 1 + this.random.nextInt(4);
            if (this.random.nextInt(10) < 6) {
                int position = 1 + this.random.nextInt(20);
                for (int pos = 0; pos < wdf; ++pos) {
                    doc.addPosting(term, new TermPos(position), new TermCount(1));
                    position += 1 + this.random.nextInt(30);
                }
            } else {
                doc.addTerm(term, new TermCount(wdf));
            }
        }
        for (long slot = 0L; slot < 5L; ++slot) {
            if (this.random.nextInt(3) == 0) {
                final String accents = "é".repeat(this.random.nextInt(3));
                doc.addValue(
                    new ValueNo(slot),
                    String.format("v%d-%s", this.random.nextInt(100), accents)
                );
            }
        }
        return doc;
    }

    private static List<String> terms() {
        final List<String> result = new ArrayList<>(Twins.WORDS);
        result.add("unknown");
        result.add("");
        return result;
    }
}
