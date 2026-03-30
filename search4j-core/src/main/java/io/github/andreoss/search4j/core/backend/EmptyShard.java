/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.api.Document;
import io.github.andreoss.search4j.core.backend.list.AllTermsList;
import io.github.andreoss.search4j.core.backend.list.EmptyPostList;
import io.github.andreoss.search4j.core.backend.list.EmptyTermList;
import io.github.andreoss.search4j.core.backend.list.LeafPostList;
import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.backend.list.TermList;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.error.InvariantError;
import io.github.andreoss.search4j.core.types.Backend;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TotalLength;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.Optional;

/**
 * Shard standing for no shard at all: a database handle with nothing added.
 *
 * <p>Statistics are zero and lists are empty; anything needing a document or
 * a writable shard raises an invalid-operation error.</p>
 *
 * @since 0.1.0
 */
public final class EmptyShard implements Shard {

    /**
     * The transaction state machine.
     */
    private final Transactions states;

    /**
     * Creates the empty shard.
     */
    public EmptyShard() {
        this.states = new Transactions(TransactionState.NONE);
    }

    @Override
    public TermCount docCount() {
        return TermCount.ZERO;
    }

    @Override
    public DocId lastDocId() {
        return DocId.NONE;
    }

    @Override
    public TotalLength totalLength() {
        return TotalLength.ZERO;
    }

    @Override
    public TermCount docLength(final DocId docid) {
        throw EmptyShard.none();
    }

    @Override
    public TermCount uniqueTerms(final DocId docid) {
        throw EmptyShard.none();
    }

    @Override
    public TermCount wdfDocMax(final DocId docid) {
        throw EmptyShard.none();
    }

    @Override
    public TermFrequencies frequencies(final String term) {
        return TermFrequencies.NONE;
    }

    @Override
    public boolean termExists(final String term) {
        return false;
    }

    @Override
    public boolean hasPositions() {
        return false;
    }

    @Override
    public ValueStats valueStats(final ValueNo slot) {
        return ValueStats.NONE;
    }

    @Override
    public TermCount docLengthLowerBound() {
        return TermCount.ZERO;
    }

    @Override
    public TermCount docLengthUpperBound() {
        return TermCount.ZERO;
    }

    @Override
    public TermCount wdfUpperBound(final String term) {
        return TermCount.ZERO;
    }

    @Override
    public PostList openPostList(final String term) {
        return EmptyPostList.INSTANCE;
    }

    @Override
    public Optional<LeafPostList> openLeafPostList(
        final String term,
        final boolean positions
    ) {
        return Optional.empty();
    }

    @Override
    public TermList openTermList(final DocId docid) {
        throw EmptyShard.none();
    }

    @Override
    public TermList openTermListDirect(final DocId docid) {
        throw EmptyShard.none();
    }

    @Override
    public AllTermsList openAllTerms(final String prefix) {
        return EmptyTermList.INSTANCE;
    }

    @Override
    public PositionList openPositionList(final DocId docid, final String term) {
        throw EmptyShard.none();
    }

    @Override
    public Optional<DocumentInternal> openDocument(
        final DocId docid,
        final boolean lazy
    ) {
        throw EmptyShard.none();
    }

    @Override
    public void addSpelling(final String word, final TermCount increment) {
        throw EmptyShard.none();
    }

    @Override
    public TermCount removeSpelling(
        final String word,
        final TermCount decrement
    ) {
        throw EmptyShard.none();
    }

    @Override
    public void addSynonym(final String term, final String synonym) {
        throw EmptyShard.none();
    }

    @Override
    public void removeSynonym(final String term, final String synonym) {
        throw EmptyShard.none();
    }

    @Override
    public void clearSynonyms(final String term) {
        throw EmptyShard.none();
    }

    @Override
    public DocId addDocument(final Document document) {
        throw EmptyShard.none();
    }

    @Override
    public void deleteDocument(final DocId docid) {
        throw EmptyShard.none();
    }

    @Override
    public void deleteDocument(final String term) {
        throw EmptyShard.none();
    }

    @Override
    public void replaceDocument(final DocId docid, final Document document) {
        throw EmptyShard.none();
    }

    @Override
    public DocId replaceDocument(final String term, final Document document) {
        throw EmptyShard.none();
    }

    @Override
    public void setMetadata(final String key, final String value) {
        throw EmptyShard.none();
    }

    @Override
    public void commit() {
        throw EmptyShard.none();
    }

    @Override
    public void cancel() {
        this.states.state();
    }

    @Override
    public void beginTransaction(final boolean flushed) {
        throw EmptyShard.none();
    }

    @Override
    public Transactions transactions() {
        return this.states;
    }

    @Override
    public void close() {
        this.states.state();
    }

    @Override
    public Revision revision() {
        return new Revision(0L);
    }

    @Override
    public TermCount size() {
        return TermCount.ZERO;
    }

    @Override
    public void release(final DocumentInternal document) {
        throw new InvariantError("The empty shard opens no document");
    }

    @Override
    public Backend backend() {
        throw new InvariantError("The empty shard has no backend");
    }

    @Override
    public String description() {
        return "";
    }

    private static InvalidOperationError none() {
        return new InvalidOperationError("No subdatabases");
    }
}
