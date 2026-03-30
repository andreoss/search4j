/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.backend.list.AllTermsList;
import io.github.andreoss.search4j.core.backend.list.EmptyTermList;
import io.github.andreoss.search4j.core.backend.list.LeafPostList;
import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.backend.list.TermList;
import io.github.andreoss.search4j.core.types.DocId;
import java.util.Optional;

/**
 * Lists and documents a shard opens.
 *
 * @since 0.1.0
 */
public interface ShardListings {

    /**
     * Opens the postings of a term.
     *
     * @param term Term to list, empty for every document
     * @return Postings, none for an unknown term
     */
    PostList openPostList(String term);

    /**
     * Opens the postings of a term for the matcher.
     *
     * @param term Term to list, empty for every document
     * @param positions True when positions will be read
     * @return Postings, empty for an unknown term
     */
    Optional<LeafPostList> openLeafPostList(String term, boolean positions);

    /**
     * Opens the terms of a document.
     *
     * @param docid Identifier of a present document
     * @return Terms with frequencies and positions
     */
    TermList openTermList(DocId docid);

    /**
     * Opens the terms of a document as stored, bypassing any combining
     * layer.
     *
     * @param docid Identifier of a present document
     * @return Terms with frequencies and positions
     */
    TermList openTermListDirect(DocId docid);

    /**
     * Opens every term starting with a prefix.
     *
     * @param prefix Leading text, empty for all terms
     * @return Terms with term frequencies
     */
    AllTermsList openAllTerms(String prefix);

    /**
     * Opens the positions of a term in a document.
     *
     * @param docid Document identifier
     * @param term Non-empty term
     * @return Positions, none when the pair is unknown
     */
    PositionList openPositionList(DocId docid, String term);

    /**
     * Opens a document.
     *
     * <p>When eager, a missing document raises a document-not-found
     * error.</p>
     *
     * @param docid Document identifier
     * @param lazy True to skip the presence check
     * @return Document, empty when lazy and known to be missing
     */
    Optional<DocumentInternal> openDocument(DocId docid, boolean lazy);

    /**
     * Opens the metadata keys starting with a prefix.
     *
     * @param prefix Leading text, empty for all keys
     * @return Keys in ascending order
     */
    default AllTermsList openMetadataKeys(final String prefix) {
        return EmptyTermList.INSTANCE;
    }
}
