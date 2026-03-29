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
 * <p>A backend with transactions stages changes until {@link #commit()} and
 * drops them on {@link #cancel()}; a backend without them applies each change
 * at once and makes both calls no-ops.</p>
 *
 * @since 0.1.0
 */
public interface WritableDatabase extends Database {

    /**
     * Adds a copy of a document under the next identifier.
     *
     * @param document The document to add
     * @return The new document identifier
     */
    DocId addDocument(Document document);

    /**
     * Deletes a document by identifier.
     *
     * <p>An absent document raises a document-not-found error.</p>
     *
     * @param docid The document identifier
     */
    void deleteDocument(DocId docid);

    /**
     * Deletes every document indexed by a term.
     *
     * @param term The non-empty term text
     */
    void deleteDocument(String term);

    /**
     * Stores a copy of a document at an identifier, adding it when absent.
     *
     * @param docid The document identifier
     * @param document The replacement document
     */
    void replaceDocument(DocId docid, Document document);

    /**
     * Replaces the first document indexed by a term and deletes the others,
     * or adds the document when the term indexes none.
     *
     * @param term The non-empty term text
     * @param document The replacement document
     * @return The identifier of the stored document
     */
    DocId replaceDocument(String term, Document document);

    /**
     * Stores metadata under a key; an empty value removes the key.
     *
     * @param key The non-empty key
     * @param value The metadata
     */
    void setMetadata(String key, String value);

    /**
     * Makes staged changes visible and durable.
     */
    void commit();

    /**
     * Discards staged changes.
     */
    void cancel();
}
