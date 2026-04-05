/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.DocId;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Set of relevant documents used to expand a query.
 *
 * <p>Documents are held in ascending identifier order; adding one twice has
 * no effect.</p>
 *
 * @since 0.1.0
 */
public final class RSet {

    /**
     * Relevant documents in ascending order.
     */
    private final NavigableSet<DocId> docs;

    /**
     * Creates an empty relevance set.
     */
    public RSet() {
        this.docs = new TreeSet<>();
    }

    /**
     * Adds a document.
     *
     * @param docid Document identifier
     */
    public void add(final DocId docid) {
        this.docs.add(docid);
    }

    /**
     * Removes a document.
     *
     * @param docid Document identifier
     */
    public void remove(final DocId docid) {
        this.docs.remove(docid);
    }

    /**
     * Reports whether a document is in the set.
     *
     * @param docid Document identifier
     * @return True when the document is relevant
     */
    public boolean contains(final DocId docid) {
        return this.docs.contains(docid);
    }

    /**
     * Number of relevant documents.
     *
     * @return Document count
     */
    public int size() {
        return this.docs.size();
    }

    /**
     * Reports whether the set is empty.
     *
     * @return True when no document is relevant
     */
    public boolean isEmpty() {
        return this.docs.isEmpty();
    }

    /**
     * Removes every document.
     */
    public void clear() {
        this.docs.clear();
    }

    /**
     * Documents in ascending order.
     *
     * @return Relevant documents
     */
    public List<DocId> docIds() {
        return List.copyOf(this.docs);
    }

    /**
     * Human-readable description.
     *
     * @return Description with the document count
     */
    public String description() {
        return String.format("RSet(%d documents)", this.docs.size());
    }
}
