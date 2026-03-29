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
     * Number of documents in the database.
     *
     * @return Document count, zero when empty
     */
    TermCount docCount();

    /**
     * Highest document identifier ever used, even if since deleted.
     *
     * @return Identifier, {@link DocId#NONE} when none was used
     */
    DocId lastDocId();

    /**
     * Average document length.
     *
     * @return Total length over document count, zero when empty
     */
    DocLength averageLength();

    /**
     * Total length of every document.
     *
     * @return Sum of all document lengths
     */
    TotalLength totalLength();

    /**
     * Number of documents indexed by a term.
     *
     * @param term Term to count, empty for every document
     * @return Document count, zero for an unknown term
     */
    TermCount termFrequency(String term);

    /**
     * Sum of the within-document frequencies of a term.
     *
     * @param term Term to count, empty for every document
     * @return Frequency sum, the document count for the empty term
     */
    TermCount collectionFrequency(String term);

    /**
     * Tests whether a term indexes any document.
     *
     * @param term Term to test, empty to test for any document
     * @return True when the term frequency is above zero
     */
    boolean termExists(String term);

    /**
     * Length of a document.
     *
     * @param docid Non-zero identifier of a present document
     * @return Sum of the within-document frequencies of its terms
     */
    TermCount docLength(DocId docid);

    /**
     * Number of distinct terms of a document.
     *
     * @param docid Non-zero identifier of a present document
     * @return Distinct term count, at most the document length
     */
    TermCount uniqueTerms(DocId docid);

    /**
     * Highest within-document frequency in a document.
     *
     * @param docid Non-zero identifier of a present document
     * @return Highest frequency, zero for an empty document
     */
    TermCount wdfDocMax(DocId docid);
}
