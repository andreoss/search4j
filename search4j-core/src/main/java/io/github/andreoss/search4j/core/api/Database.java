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
 * A read-only view over a database.
 *
 * <p>Documents are addressed by identifier and terms by their text. A
 * document length is the sum of the within-document frequencies of its terms.
 * Identifier zero is rejected with an invalid-argument error, a missing
 * document with a document-not-found error, and any use after
 * {@link #close()} with a database-closed error.</p>
 *
 * @since 0.1.0
 */
public interface Database
    extends Statistics, Bounds, Listings, AutoCloseable {

    /**
     * Adds another database to this handle for combined search.
     *
     * @param database The database to add
     */
    void addDatabase(Database database);

    /**
     * Returns the metadata stored under a key.
     *
     * @param key The non-empty key
     * @return The metadata, empty when unset
     */
    String metadata(String key);

    /**
     * Returns a detached copy of a document.
     *
     * @param docid The document identifier
     * @return The document
     */
    Document document(DocId docid);

    @Override
    void close();
}
