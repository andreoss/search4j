/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.DocId;

/**
 * A database that can be written to.
 *
 * <p>Changes are staged until {@link #commit()} makes them visible and
 * durable; {@link #cancel()} discards the staged changes.</p>
 *
 * @since 0.1.0
 */
public interface WritableDatabase extends Database {

    /**
     * Adds a document and returns its identifier.
     *
     * @param document The document to add
     * @return The new document identifier
     */
    DocId addDocument(Document document);

    /**
     * Deletes a document by identifier.
     *
     * @param docid The document identifier
     */
    void deleteDocument(DocId docid);

    /**
     * Replaces the document at an identifier.
     *
     * @param docid The document identifier
     * @param document The replacement document
     */
    void replaceDocument(DocId docid, Document document);

    /**
     * Makes staged changes visible and durable.
     */
    void commit();

    /**
     * Discards staged changes.
     */
    void cancel();
}
