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
public interface WritableDatabase extends Database, Transactional {

    /**
     * Adds a copy of a document under the next identifier.
     *
     * @param document Document to copy in
     * @return One above the last identifier ever used
     */
    DocId addDocument(Document document);

    /**
     * Deletes a document by identifier.
     *
     * <p>An absent document raises a document-not-found error.</p>
     *
     * @param docid Non-zero document identifier
     */
    void deleteDocument(DocId docid);

    /**
     * Deletes every document indexed by a term.
     *
     * @param term Non-empty term; an unknown term deletes nothing
     */
    void deleteDocument(String term);

    /**
     * Stores a copy of a document at an identifier, adding it when absent.
     *
     * @param docid Non-zero document identifier
     * @param document Document to copy in
     */
    void replaceDocument(DocId docid, Document document);

    /**
     * Replaces the first document indexed by a term and deletes the others,
     * or adds the document when the term indexes none.
     *
     * @param term Non-empty term
     * @param document Document to copy in
     * @return Lowest identifier indexed by the term, or a new identifier
     */
    DocId replaceDocument(String term, Document document);

    /**
     * Stores metadata under a key; an empty value removes the key.
     *
     * @param key Non-empty key
     * @param value Value to store, empty to remove
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
