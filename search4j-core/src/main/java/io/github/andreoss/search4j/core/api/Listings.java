/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;

/**
 * Iterators over the contents of a database.
 *
 * @since 0.1.0
 */
public interface Listings {

    /**
     * Opens the postings of a term; the empty term iterates every document
     * with a frequency of one.
     *
     * @param term The term text
     * @return The posting iterator, empty for an unknown term
     */
    PostingIterator postings(String term);

    /**
     * Opens the terms of a document.
     *
     * @param docid The document identifier
     * @return The term iterator
     */
    TermIterator termList(DocId docid);

    /**
     * Opens the positions of a term in a document.
     *
     * @param docid The document identifier
     * @param term The term text
     * @return The position iterator, empty when the pair is unknown
     */
    PositionIterator positions(DocId docid, String term);

    /**
     * Returns the number of positions of a term in a document.
     *
     * @param docid The document identifier
     * @param term The term text
     * @return The position count, zero when the pair is unknown
     */
    TermCount positionCount(DocId docid, String term);

    /**
     * Opens every term in use starting with a prefix.
     *
     * @param prefix The term prefix, empty for all terms
     * @return The term iterator
     */
    TermIterator allTerms(String prefix);

    /**
     * Opens the values of a slot across documents.
     *
     * @param slot The value slot number
     * @return The value iterator
     */
    ValueIterator valueStream(ValueNo slot);

    /**
     * Opens the metadata keys starting with a prefix.
     *
     * @param prefix The key prefix, empty for all keys
     * @return The key iterator
     */
    TermIterator metadataKeys(String prefix);
}
