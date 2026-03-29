/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.api.Database;
import io.github.andreoss.search4j.core.api.Document;
import io.github.andreoss.search4j.core.api.PositionIterator;
import io.github.andreoss.search4j.core.api.PostingIterator;
import io.github.andreoss.search4j.core.api.TermIterator;
import io.github.andreoss.search4j.core.api.ValueIterator;
import io.github.andreoss.search4j.core.api.WritableDatabase;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.UnimplementedError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.DocLength;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.TotalLength;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.Collections;
import java.util.List;

/**
 * Database held entirely in memory, mainly for testing.
 *
 * <p>Changes apply at once, so there are no transactions: commit and cancel
 * only check the database is open. Documents are copied in and out. After
 * {@link #close()}, the total length and the document length bounds stay
 * readable; every other call raises a database-closed error.</p>
 *
 * @since 0.1.0
 */
public final class InMemoryDatabase implements WritableDatabase {

    /**
     * The contents.
     */
    private final Store store;

    /**
     * Creates an empty in-memory database.
     */
    public InMemoryDatabase() {
        this.store = new Store();
    }

    @Override
    public void addDatabase(final Database database) {
        throw new UnimplementedError(
            "Combining databases is not supported by the in-memory backend"
        );
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
    public DocLength averageLength() {
        final int count = this.docCount().value();
        DocLength result = DocLength.ZERO;
        if (count > 0) {
            result = new DocLength((double) this.store.total() / count);
        }
        return result;
    }

    @Override
    public TotalLength totalLength() {
        return new TotalLength(this.store.total());
    }

    @Override
    public TermCount termFrequency(final String term) {
        TermCount result = this.docCount();
        if (!term.isEmpty()) {
            result = this.store.frequency(term);
        }
        return result;
    }

    @Override
    public TermCount collectionFrequency(final String term) {
        TermCount result = this.docCount();
        if (!term.isEmpty()) {
            result = this.store.collection(term);
        }
        return result;
    }

    @Override
    public boolean termExists(final String term) {
        return this.termFrequency(term).value() > 0;
    }

    @Override
    public TermCount docLength(final DocId docid) {
        return this.stored(docid).length();
    }

    @Override
    public TermCount uniqueTerms(final DocId docid) {
        return this.stored(docid).unique();
    }

    @Override
    public TermCount wdfDocMax(final DocId docid) {
        return this.stored(docid).highest();
    }

    @Override
    public TermCount docLengthLowerBound() {
        return new TermCount(1);
    }

    @Override
    public TermCount docLengthUpperBound() {
        return new TermCount(
            Math.toIntExact(Math.min(this.store.total(), Integer.MAX_VALUE))
        );
    }

    @Override
    public TermCount wdfUpperBound(final String term) {
        TermCount result = TermCount.ZERO;
        if (!term.isEmpty()) {
            result = this.collectionFrequency(term);
        }
        return result;
    }

    @Override
    public TermCount valueFrequency(final ValueNo slot) {
        this.store.live();
        return this.store.slot(slot).frequency();
    }

    @Override
    public String valueLowerBound(final ValueNo slot) {
        this.store.live();
        return this.store.slot(slot).lower();
    }

    @Override
    public String valueUpperBound(final ValueNo slot) {
        this.store.live();
        return this.store.slot(slot).upper();
    }

    @Override
    public boolean hasPositions() {
        this.store.live();
        return this.store.positional();
    }

    @Override
    public PostingIterator postings(final String term) {
        this.store.live();
        final PostingIterator result;
        if (term.isEmpty()) {
            result = new AllDocsCursor(this.store, this.store.docs());
        } else {
            result = new PostingCursor(this.store, this.store.postings(term));
        }
        return result;
    }

    @Override
    public TermIterator termList(final DocId docid) {
        return new TermCursor(this.stored(docid).terms(), this.store);
    }

    @Override
    public PositionIterator positions(final DocId docid, final String term) {
        return new PositionCursor(this.located(docid, term));
    }

    @Override
    public TermCount positionCount(final DocId docid, final String term) {
        return new TermCount(this.located(docid, term).size());
    }

    @Override
    public TermIterator allTerms(final String prefix) {
        this.store.live();
        return new PrefixCursor<>(this.store.terms(), prefix, this.store);
    }

    @Override
    public ValueIterator valueStream(final ValueNo slot) {
        this.store.live();
        return new StreamCursor(this.store, this.store.docs(), slot);
    }

    @Override
    public String metadata(final String key) {
        InMemoryDatabase.keyed(key);
        this.store.live();
        return this.store.meta().getOrDefault(key, "");
    }

    @Override
    public TermIterator metadataKeys(final String prefix) {
        this.store.live();
        return new PrefixCursor<>(
            this.store.meta(), prefix, new Keys(this.store)
        );
    }

    @Override
    public Document document(final DocId docid) {
        return this.stored(docid).document();
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
    public void deleteDocument(final String term) {
        InMemoryDatabase.termed(term);
        this.store.live();
        for (final DocId docid : this.store.indexed(term)) {
            this.store.delete(docid);
        }
    }

    @Override
    public void replaceDocument(final DocId docid, final Document document) {
        InMemoryDatabase.valid(docid);
        this.store.live();
        this.store.replace(docid, Stored.of(document));
    }

    @Override
    public DocId replaceDocument(final String term, final Document document) {
        InMemoryDatabase.termed(term);
        this.store.live();
        final List<DocId> found = this.store.indexed(term);
        final DocId result;
        if (found.isEmpty()) {
            result = this.store.add(Stored.of(document));
        } else {
            result = found.get(0);
            this.store.replace(result, Stored.of(document));
            for (final DocId docid : found.subList(1, found.size())) {
                this.store.delete(docid);
            }
        }
        return result;
    }

    @Override
    public void setMetadata(final String key, final String value) {
        InMemoryDatabase.keyed(key);
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
    public void close() {
        this.store.close();
    }

    private Stored stored(final DocId docid) {
        InMemoryDatabase.valid(docid);
        this.store.live();
        return this.store.stored(docid);
    }

    private List<TermPos> located(final DocId docid, final String term) {
        InMemoryDatabase.valid(docid);
        InMemoryDatabase.termed(term);
        this.store.live();
        List<TermPos> result = Collections.emptyList();
        final Stored found = this.store.docs().get(docid);
        if (found != null && found.terms().containsKey(term)) {
            result = found.terms().get(term).positions();
        }
        return result;
    }

    private static void valid(final DocId docid) {
        if (!docid.valid()) {
            throw new InvalidArgumentError("Document identifier 0 is invalid");
        }
    }

    private static void termed(final String term) {
        if (term.isEmpty()) {
            throw new InvalidArgumentError("Empty terms are invalid");
        }
    }

    private static void keyed(final String key) {
        if (key.isEmpty()) {
            throw new InvalidArgumentError("Empty metadata keys are invalid");
        }
    }
}
