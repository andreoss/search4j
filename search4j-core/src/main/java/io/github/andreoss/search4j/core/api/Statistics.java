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
 * Document and term statistics of a database.
 *
 * @since 0.1.0
 */
public interface Statistics {

    /**
     * Returns the number of documents in the database.
     *
     * @return The document count
     */
    TermCount docCount();

    /**
     * Returns the highest document identifier ever used.
     *
     * @return The last document identifier
     */
    DocId lastDocId();

    /**
     * Returns the average document length, zero when empty.
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
     * Returns the number of documents indexed by a term; the empty term
     * stands for every document.
     *
     * @param term The term text
     * @return The term frequency
     */
    TermCount termFrequency(String term);

    /**
     * Returns the sum of the within-document frequencies of a term; the empty
     * term stands for every document.
     *
     * @param term The term text
     * @return The collection frequency
     */
    TermCount collectionFrequency(String term);

    /**
     * Tests whether a term indexes any document; the empty term tests whether
     * any document exists.
     *
     * @param term The term text
     * @return True when the term is in use
     */
    boolean termExists(String term);

    /**
     * Returns the length of a document.
     *
     * @param docid The document identifier
     * @return The sum of within-document frequencies of its terms
     */
    TermCount docLength(DocId docid);

    /**
     * Returns the number of distinct terms of a document, at most its length.
     *
     * @param docid The document identifier
     * @return The distinct term count
     */
    TermCount uniqueTerms(DocId docid);

    /**
     * Returns the highest within-document frequency in a document.
     *
     * @param docid The document identifier
     * @return The highest frequency
     */
    TermCount wdfDocMax(DocId docid);
}
