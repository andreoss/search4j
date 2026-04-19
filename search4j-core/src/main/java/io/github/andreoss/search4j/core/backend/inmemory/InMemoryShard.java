/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.api.Document;
import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.backend.Shard;
import io.github.andreoss.search4j.core.backend.TermFrequencies;
import io.github.andreoss.search4j.core.backend.TransactionState;
import io.github.andreoss.search4j.core.backend.Transactions;
import io.github.andreoss.search4j.core.backend.ValueStats;
import io.github.andreoss.search4j.core.backend.list.AllTermsList;
import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.backend.list.LeafPostList;
import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.backend.list.TermList;
import io.github.andreoss.search4j.core.backend.list.ValueList;
import io.github.andreoss.search4j.core.backend.list.VectorPositionList;
import io.github.andreoss.search4j.core.types.Backend;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TotalLength;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.Objects;
import java.util.Optional;

/**
 * Shard held entirely in memory, mainly for testing.
 *
 * <p>Changes apply at once, so there are no transactions: commit and cancel
 * only check the shard is open. Documents are copied in. After
 * {@link #close()}, the total length and the document length bounds stay
 * readable; every other call raises a database-closed error.</p>
 *
 * @since 0.1.0
 */
public final class InMemoryShard implements Shard {

    /**
     * The contents.
     */
    private final Store store;

    /**
     * The transaction state machine.
     */
    private final Transactions states;

    /**
     * Creates an empty in-memory shard.
     */
    public InMemoryShard() {
        this.store = new Store();
        this.states = new Transactions(TransactionState.UNIMPLEMENTED);
    }

    @Override
    public DocRange usedDocIds() {
        this.store.live();
        return this.store.used();
    }

    @Override
    public TermCount docCount() {
        this.store.live();
        return new TermCount(this.store.docs().size());
    }

    @Override
    public DocId lastDocId() {
        this.store.live();
        return this.store.last();
    }

    @Override
    public TotalLength totalLength() {
        return new TotalLength(this.store.total());
    }

    @Override
    public TermCount docLength(final DocId docid) {
        this.store.live();
        return this.store.stored(docid).length();
    }

    @Override
    public TermCount uniqueTerms(final DocId docid) {
        this.store.live();
        return this.store.stored(docid).unique();
    }

    @Override
    public TermCount wdfDocMax(final DocId docid) {
        this.store.live();
        return this.store.stored(docid).highest();
    }

    @Override
    public TermFrequencies frequencies(final String term) {
        this.store.live();
        return new TermFrequencies(
            this.store.frequency(term), this.store.collection(term)
        );
    }

    @Override
    public boolean termExists(final String term) {
        this.store.live();
        return this.store.frequency(term).value() > 0;
    }

    @Override
    public boolean hasPositions() {
        this.store.live();
        return this.store.positional();
    }

    @Override
    public ValueStats valueStats(final ValueNo slot) {
        this.store.live();
        return this.store.slot(slot).snapshot();
    }

    @Override
    public TermCount docLengthLowerBound() {
        return new TermCount(1);
    }

    @Override
    public TermCount docLengthUpperBound() {
        return new TotalLength(this.store.total()).clamped();
    }

    @Override
    public TermCount wdfUpperBound(final String term) {
        this.store.live();
        return this.store.collection(term);
    }

    @Override
    public PostList openPostList(final String term) {
        this.store.live();
        final PostList result;
        if (term.isEmpty()) {
            result = new AllDocsPostList(this.store);
        } else {
            result = new InMemoryPostList(this.store, term);
        }
        return result;
    }

    @Override
    public Optional<LeafPostList> openLeafPostList(
        final String term,
        final boolean positions
    ) {
        this.store.live();
        Optional<LeafPostList> result = Optional.empty();
        if (term.isEmpty()) {
            result = Optional.of(new AllDocsPostList(this.store));
        } else if (this.store.frequency(term).value() > 0) {
            result = Optional.of(new InMemoryPostList(this.store, term));
        }
        return result;
    }

    @Override
    public TermList openTermList(final DocId docid) {
        this.store.live();
        return new InMemoryTermList(this.store.stored(docid).terms(), this.store);
    }

    @Override
    public TermList openTermListDirect(final DocId docid) {
        return this.openTermList(docid);
    }

    @Override
    public AllTermsList openAllTerms(final String prefix) {
        this.store.live();
        return new PrefixList<>(this.store.terms(), prefix, this.store);
    }

    @Override
    public PositionList openPositionList(final DocId docid, final String term) {
        this.store.live();
        return new VectorPositionList(this.store.positions(docid, term));
    }

    @Override
    public Optional<DocumentInternal> openDocument(
        final DocId docid,
        final boolean lazy
    ) {
        this.store.live();
        Optional<Stored> found = Optional.ofNullable(this.store.docs().get(docid));
        if (!lazy) {
            found = Optional.of(this.store.stored(docid));
        }
        return found.map(
            stored -> new DocumentInternal(
                docid, new StoredSource(stored, this.store)
            )
        );
    }

    @Override
    public AllTermsList openMetadataKeys(final String prefix) {
        this.store.live();
        return new PrefixList<>(this.store.meta(), prefix, new Keys(this.store));
    }

    @Override
    public ValueList openValueList(final ValueNo slot) {
        this.store.live();
        return new InMemoryValueList(this.store, slot);
    }

    @Override
    public DocId addDocument(final Document document) {
        this.store.live();
        return this.store.add(Stored.of(document));
    }

    @Override
    public void deleteDocument(final DocId docid) {
        this.store.live();
        this.store.delete(docid);
    }

    @Override
    public void replaceDocument(final DocId docid, final Document document) {
        this.store.live();
        this.store.replace(docid, Stored.of(document));
    }

    @Override
    public String metadata(final String key) {
        this.store.live();
        return this.store.meta().getOrDefault(key, "");
    }

    @Override
    public void setMetadata(final String key, final String value) {
        this.store.live();
        if (value.isEmpty()) {
            this.store.meta().remove(key);
        } else {
            this.store.meta().put(key, value);
        }
    }

    @Override
    public void commit() {
        this.store.live();
    }

    @Override
    public void cancel() {
        this.store.live();
    }

    @Override
    public Transactions transactions() {
        return this.states;
    }

    @Override
    public boolean reopen() {
        this.store.live();
        return false;
    }

    @Override
    public void close() {
        this.store.close();
    }

    @Override
    public TermCount size() {
        return new TermCount(1);
    }

    @Override
    public void release(final DocumentInternal document) {
        Objects.requireNonNull(document, "document");
    }

    @Override
    public Backend backend() {
        return Backend.IN_MEMORY;
    }

    @Override
    public String description() {
        return "InMemoryShard";
    }
}
