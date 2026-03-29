/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.DocLength;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TotalLength;

/**
 * A read-only view over a database.
 *
 * <p>Multiple databases can be searched as one by adding them to a single
 * handle. Documents are addressed by their identifier and terms by their
 * text.</p>
 *
 * @since 0.1.0
 */
public interface Database extends AutoCloseable {

    /**
     * Adds another database to this handle for combined search.
     *
     * @param database The database to add
     */
    void addDatabase(Database database);

    /**
     * Returns the number of documents in the database.
     *
     * @return The document count
     */
    TermCount docCount();

    /**
     * Returns the highest document identifier in use.
     *
     * @return The last document identifier
     */
    DocId lastDocId();

    /**
     * Returns the average document length.
     *
     * @return The average length
     */
    DocLength averageLength();

    /**
     * Returns the total length of every document.
     *
     * @return The total length
     */
    TotalLength totalLength();

    /**
     * Returns the frequency of a term across the database.
     *
     * @param term The term text
     * @return The term frequency
     */
    TermCount termFrequency(String term);

    /**
     * Returns the length of a document.
     *
     * @param docid The document identifier
     * @return The document length
     */
    TermCount docLength(DocId docid);

    /**
     * Returns a document by identifier.
     *
     * @param docid The document identifier
     * @return The document
     */
    Document document(DocId docid);

    @Override
    void close();
}
