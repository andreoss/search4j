/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Postings and collection frequency of one term in a store.
 *
 * @since 0.1.0
 */
final class Term {

    /**
     * The occurrences by document.
     */
    private final NavigableMap<DocId, Occurrence> postings;

    /**
     * The sum of the within-document frequencies.
     */
    private TermCount collection;

    /**
     * Creates a term indexing no document.
     */
    Term() {
        this.postings = new TreeMap<>(Orders.DOCS);
        this.collection = TermCount.ZERO;
    }

    /**
     * Records an occurrence in a document.
     *
     * @param docid The document identifier
     * @param occurrence The occurrence
     */
    void post(final DocId docid, final Occurrence occurrence) {
        this.postings.put(docid, occurrence);
        this.collection = this.collection.plus(occurrence.wdf());
    }

    /**
     * Forgets the occurrence in a document.
     *
     * @param docid The document identifier
     * @param occurrence The occurrence
     */
    void unpost(final DocId docid, final Occurrence occurrence) {
        this.postings.remove(docid);
        this.collection = new TermCount(
            this.collection.value() - occurrence.wdf().value()
        );
    }

    /**
     * Number of documents indexed.
     *
     * @return Term frequency
     */
    TermCount frequency() {
        return new TermCount(this.postings.size());
    }

    /**
     * Sum of the within-document frequencies.
     *
     * @return Collection frequency
     */
    TermCount collection() {
        return this.collection;
    }

    /**
     * Live occurrences by document.
     *
     * @return Postings
     */
    NavigableMap<DocId, Occurrence> postings() {
        return this.postings;
    }
}
