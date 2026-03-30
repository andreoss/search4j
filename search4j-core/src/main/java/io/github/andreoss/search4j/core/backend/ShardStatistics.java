/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TotalLength;

/**
 * Document and term statistics a shard provides.
 *
 * <p>Callers pass non-zero identifiers and non-empty terms; a missing
 * document raises a document-not-found error.</p>
 *
 * @since 0.1.0
 */
public interface ShardStatistics {

    /**
     * Number of documents.
     *
     * @return Document count
     */
    TermCount docCount();

    /**
     * Highest document identifier ever used.
     *
     * @return Identifier, {@link DocId#NONE} when none was used
     */
    DocId lastDocId();

    /**
     * Total length of every document.
     *
     * @return Sum of all document lengths
     */
    TotalLength totalLength();

    /**
     * Length of a document.
     *
     * @param docid Identifier of a present document
     * @return Sum of the within-document frequencies of its terms
     */
    TermCount docLength(DocId docid);

    /**
     * Number of distinct terms of a document.
     *
     * @param docid Identifier of a present document
     * @return Distinct term count
     */
    TermCount uniqueTerms(DocId docid);

    /**
     * Highest within-document frequency in a document.
     *
     * @param docid Identifier of a present document
     * @return Highest frequency, zero for an empty document
     */
    TermCount wdfDocMax(DocId docid);

    /**
     * Frequencies of a term.
     *
     * @param term Non-empty term
     * @return Frequencies, {@link TermFrequencies#NONE} for an unknown term
     */
    TermFrequencies frequencies(String term);

    /**
     * Tests whether a term indexes any document.
     *
     * @param term Non-empty term
     * @return True when the term frequency is above zero
     */
    boolean termExists(String term);

    /**
     * Tests whether any document carries term positions.
     *
     * @return True when positions may be present
     */
    boolean hasPositions();
}
