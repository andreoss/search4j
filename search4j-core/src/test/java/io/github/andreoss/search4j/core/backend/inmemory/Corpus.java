/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the in-memory database backend.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.api.PositionIterator;
import io.github.andreoss.search4j.core.api.PostingIterator;
import io.github.andreoss.search4j.core.api.TermIterator;
import io.github.andreoss.search4j.core.api.ValueIterator;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared fixture: three documents and iterator readers.
 *
 * <p>Document 1 has data "one", values b and x in slots 1 and 2, "apple" at
 * positions 1 and 5 and "banana" with frequency 3. Document 2 has data "two",
 * value a in slot 1, "apple" at 2 and "cherry" at 3. Document 3 has data
 * "three" and "banana" with frequency 1.</p>
 *
 * @since 0.1.0
 */
final class Corpus {

    /**
     * Utility class, no instances.
     */
    private Corpus() {
    }

    /**
     * Builds the fixture database.
     *
     * @return The database
     */
    static InMemoryDatabase build() {
        final InMemoryDatabase database = new InMemoryDatabase();
        database.addDocument(Corpus.first());
        database.addDocument(Corpus.second());
        database.addDocument(Corpus.third());
        return database;
    }

    /**
     * Builds the first fixture document.
     *
     * @return The document
     */
    static InMemoryDocument first() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.setData("one");
        doc.addValue(new ValueNo(1L), "b");
        doc.addValue(new ValueNo(2L), "x");
        doc.addPosting("apple", new TermPos(5), new TermCount(1));
        doc.addPosting("apple", new TermPos(1), new TermCount(1));
        doc.addTerm("banana", new TermCount(3));
        return doc;
    }

    /**
     * Builds the second fixture document.
     *
     * @return The document
     */
    static InMemoryDocument second() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.setData("two");
        doc.addValue(new ValueNo(1L), "a");
        doc.addPosting("apple", new TermPos(2), new TermCount(1));
        doc.addPosting("cherry", new TermPos(3), new TermCount(1));
        return doc;
    }

    /**
     * Builds the third fixture document.
     *
     * @return The document
     */
    static InMemoryDocument third() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.setData("three");
        doc.addTerm("banana", new TermCount(1));
        return doc;
    }

    /**
     * Builds a document holding one unpositioned term.
     *
     * @param term The term text
     * @return The document
     */
    static InMemoryDocument plain(final String term) {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.setData(term);
        doc.addTerm(term, new TermCount(1));
        return doc;
    }

    /**
     * Drains a posting iterator.
     *
     * @param iter The iterator
     * @return The document identifiers
     */
    static List<DocId> docs(final PostingIterator iter) {
        final List<DocId> result = new ArrayList<>(3);
        while (iter.hasNext()) {
            result.add(iter.next());
        }
        return result;
    }

    /**
     * Drains a posting iterator into frequencies.
     *
     * @param iter The iterator
     * @return The within-document frequencies
     */
    static List<TermCount> wdfs(final PostingIterator iter) {
        final List<TermCount> result = new ArrayList<>(3);
        while (iter.hasNext()) {
            iter.next();
            result.add(iter.wdf());
        }
        return result;
    }

    /**
     * Drains a term iterator.
     *
     * @param iter The iterator
     * @return The terms
     */
    static List<String> terms(final TermIterator iter) {
        final List<String> result = new ArrayList<>(3);
        while (iter.hasNext()) {
            result.add(iter.next());
        }
        return result;
    }

    /**
     * Drains a position iterator.
     *
     * @param iter The iterator
     * @return The positions
     */
    static List<TermPos> positions(final PositionIterator iter) {
        final List<TermPos> result = new ArrayList<>(3);
        while (iter.hasNext()) {
            result.add(iter.next());
        }
        return result;
    }

    /**
     * Drains a value iterator into "docid:value" entries.
     *
     * @param iter The iterator
     * @return The entries
     */
    static List<String> values(final ValueIterator iter) {
        final List<String> result = new ArrayList<>(3);
        while (iter.hasNext()) {
            final String value = iter.next();
            result.add(String.format("%s:%s", iter.docId(), value));
        }
        return result;
    }

    /**
     * Builds a list of positions.
     *
     * @param values The position numbers
     * @return The positions
     */
    static List<TermPos> at(final int... values) {
        final List<TermPos> result = new ArrayList<>(values.length);
        for (final int value : values) {
            result.add(new TermPos(value));
        }
        return result;
    }

    /**
     * Builds a list of document identifiers.
     *
     * @param values The identifier numbers
     * @return The identifiers
     */
    static List<DocId> ids(final int... values) {
        final List<DocId> result = new ArrayList<>(values.length);
        for (final int value : values) {
            result.add(DocId.of(value));
        }
        return result;
    }
}
