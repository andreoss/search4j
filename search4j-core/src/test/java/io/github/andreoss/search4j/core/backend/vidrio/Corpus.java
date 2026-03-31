/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;

/**
 * Documents indexed into a posting list table through the merge, with a
 * model of the postings expected.
 *
 * @since 0.1.0
 */
final class Corpus {

    /**
     * Term in every document.
     */
    static final String EVERY = "every";

    /**
     * Source of randomness.
     */
    private final Random random;

    /**
     * Blocks written.
     */
    private final MemoryBlocks blocks;

    /**
     * Table written.
     */
    private final TableWriter table;

    /**
     * Merge into the table.
     */
    private final PostingWriter writer;

    /**
     * Terms of every document, with their frequency.
     */
    private final NavigableMap<DocId, Map<String, TermCount>> docs;

    /**
     * Changes not merged yet, by term.
     */
    private final Map<String, PostingChanges> pending;

    /**
     * Vocabulary of the random documents.
     */
    private final List<String> words;

    /**
     * Length changes not merged yet.
     */
    private PostingChanges lens;

    /**
     * Creates a corpus from its parts.
     *
     * @param random Source of randomness
     * @param blocks Blocks written
     * @param table Table written
     * @param words Vocabulary of the random documents
     */
    private Corpus(
        final Random random, final MemoryBlocks blocks,
        final TableWriter table, final List<String> words
    ) {
        this.random = random;
        this.blocks = blocks;
        this.table = table;
        this.writer = new PostingWriter(table);
        this.docs = new TreeMap<>();
        this.pending = new TreeMap<>();
        this.words = words;
        this.lens = new PostingChanges();
    }

    /**
     * Creates an empty corpus.
     *
     * @param seed Seed of the randomness
     * @param size Block size
     * @param vocabulary Number of random words
     * @return Corpus
     */
    static Corpus of(final long seed, final BlockSize size, final int vocabulary) {
        final List<String> words = new ArrayList<>(vocabulary);
        for (int idx = 0; idx < vocabulary; ++idx) {
            words.add(String.format("w%d", idx));
        }
        final MemoryBlocks blocks = new MemoryBlocks(new HashMap<>(), size);
        return new Corpus(
            new Random(seed), blocks, TableWriter.create(blocks, 0), words
        );
    }

    /**
     * Adds a document with random terms.
     *
     * @param doc Document, not present
     * @param count Number of random terms, besides {@link #EVERY}
     */
    void addRandom(final DocId doc, final int count) {
        final Map<String, TermCount> terms = new TreeMap<>();
        terms.put(Corpus.EVERY, new TermCount(1 + this.random.nextInt(3)));
        for (int idx = 0; idx < count; ++idx) {
            terms.put(
                this.words.get(this.random.nextInt(this.words.size())),
                new TermCount(this.random.nextInt(5))
            );
        }
        this.add(doc, terms);
    }

    /**
     * Adds a document.
     *
     * @param doc Document, not present
     * @param terms Its terms with their frequency
     */
    void add(final DocId doc, final Map<String, TermCount> terms) {
        if (this.docs.containsKey(doc)) {
            throw new IllegalArgumentException("Document present");
        }
        for (final Map.Entry<String, TermCount> term : terms.entrySet()) {
            this.changes(term.getKey()).add(doc, term.getValue());
        }
        this.lens.add(doc, Corpus.length(terms));
        this.docs.put(doc, new TreeMap<>(terms));
    }

    /**
     * Deletes a document.
     *
     * @param doc Document present
     */
    void delete(final DocId doc) {
        final Map<String, TermCount> terms = this.docs.remove(doc);
        for (final Map.Entry<String, TermCount> term : terms.entrySet()) {
            this.changes(term.getKey()).remove(doc, term.getValue());
        }
        this.lens.remove(doc, Corpus.length(terms));
    }

    /**
     * Replaces the terms of a document.
     *
     * @param doc Document present
     * @param terms Its new terms with their frequency
     */
    void replace(final DocId doc, final Map<String, TermCount> terms) {
        final Map<String, TermCount> old = this.docs.get(doc);
        for (final Map.Entry<String, TermCount> term : old.entrySet()) {
            if (terms.containsKey(term.getKey())) {
                this.changes(term.getKey()).update(
                    doc, term.getValue(), terms.get(term.getKey())
                );
            } else {
                this.changes(term.getKey()).remove(doc, term.getValue());
            }
        }
        for (final Map.Entry<String, TermCount> term : terms.entrySet()) {
            if (!old.containsKey(term.getKey())) {
                this.changes(term.getKey()).add(doc, term.getValue());
            }
        }
        this.lens.update(doc, Corpus.length(old), Corpus.length(terms));
        this.docs.put(doc, new TreeMap<>(terms));
    }

    /**
     * Merges the pending changes and commits a revision.
     *
     * @param revision Revision number
     * @return Root information of the revision
     */
    TableState commit(final long revision) {
        for (final Map.Entry<String, PostingChanges> term
            : this.pending.entrySet()) {
            this.writer.merge(term.getKey(), term.getValue());
        }
        this.pending.clear();
        this.writer.mergeLengths(this.lens);
        this.lens = new PostingChanges();
        return this.table.commit(new RevisionNumber(revision));
    }

    /**
     * Opens a committed revision to read.
     *
     * @param state Root information of the revision
     * @return Table
     */
    VidrioTable table(final TableState state) {
        return VidrioTable.over(this.blocks, state.root());
    }

    /**
     * Table written.
     *
     * @return Writer
     */
    TableWriter writer() {
        return this.table;
    }

    /**
     * Documents present.
     *
     * @return Documents in order
     */
    List<DocId> docs() {
        return new ArrayList<>(this.docs.keySet());
    }

    /**
     * Terms of a document.
     *
     * @param doc Document present
     * @return Its terms with their frequency
     */
    Map<String, TermCount> terms(final DocId doc) {
        return this.docs.get(doc);
    }

    /**
     * Postings of a term as the model expects them.
     *
     * @param term Term
     * @return Document and frequency pairs in document order
     */
    List<String> expected(final String term) {
        final List<String> result = new ArrayList<>(this.docs.size());
        for (final Map.Entry<DocId, Map<String, TermCount>> doc
            : this.docs.entrySet()) {
            final TermCount wdf = doc.getValue().get(term);
            if (wdf != null) {
                result.add(Readback.posting(doc.getKey(), wdf));
            }
        }
        return result;
    }

    /**
     * Lengths of every document as the model expects them.
     *
     * @return Document and length pairs in document order
     */
    List<String> expectedLengths() {
        final List<String> result = new ArrayList<>(this.docs.size());
        for (final Map.Entry<DocId, Map<String, TermCount>> doc
            : this.docs.entrySet()) {
            result.add(
                Readback.posting(doc.getKey(), Corpus.length(doc.getValue()))
            );
        }
        return result;
    }

    private PostingChanges changes(final String term) {
        return this.pending.computeIfAbsent(term, key -> new PostingChanges());
    }

    private static TermCount length(final Map<String, TermCount> terms) {
        TermCount total = TermCount.ZERO;
        for (final TermCount wdf : terms.values()) {
            total = total.plus(wdf);
        }
        return total;
    }
}
