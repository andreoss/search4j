/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.error.DatabaseClosedError;
import io.github.andreoss.search4j.core.error.DocNotFoundError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Documents, postings, value statistics and metadata held in memory.
 *
 * <p>Every change applies at once. Closing drops the contents and makes
 * further use fail with a database-closed error.</p>
 *
 * @since 0.1.0
 */
final class Store implements Stats {

    /**
     * The terms in use, ordered by {@link TermOrder}.
     */
    private final NavigableMap<String, Term> terms;

    /**
     * The documents by identifier.
     */
    private final NavigableMap<DocId, Stored> docs;

    /**
     * The value statistics by slot.
     */
    private final Map<ValueNo, SlotStats> slots;

    /**
     * The metadata by key, ordered by {@link TermOrder}.
     */
    private final NavigableMap<String, String> meta;

    /**
     * The highest document identifier ever used.
     */
    private DocId last;

    /**
     * The total length of every document.
     */
    private long total;

    /**
     * Whether a document with positions was added since the store was last
     * empty.
     */
    private boolean positional;

    /**
     * Whether the store was closed.
     */
    private boolean closed;

    /**
     * Creates an empty store.
     */
    Store() {
        this.terms = new TreeMap<>(TermOrder.INSTANCE);
        this.docs = new TreeMap<>(Orders.DOCS);
        this.slots = new TreeMap<>(Orders.SLOTS);
        this.meta = new TreeMap<>(TermOrder.INSTANCE);
        this.last = DocId.NONE;
        this.total = 0L;
        this.positional = false;
        this.closed = false;
    }

    @Override
    public boolean live() {
        if (this.closed) {
            throw new DatabaseClosedError("Database is closed");
        }
        return true;
    }

    @Override
    public TermCount frequency(final String term) {
        return this.term(term).map(Term::frequency).orElse(TermCount.ZERO);
    }

    /**
     * Returns the collection frequency of a term.
     *
     * @param term The term text
     * @return The collection frequency
     */
    TermCount collection(final String term) {
        return this.term(term).map(Term::collection).orElse(TermCount.ZERO);
    }

    /**
     * Returns the live postings of a term.
     *
     * @param term The term text
     * @return The occurrences by document, empty for an unknown term
     */
    NavigableMap<DocId, Occurrence> postings(final String term) {
        return this.term(term).map(Term::postings).orElseGet(
            () -> Collections.unmodifiableNavigableMap(
                new TreeMap<DocId, Occurrence>(Orders.DOCS)
            )
        );
    }

    /**
     * Returns the live terms in use.
     *
     * @return The terms, ordered by {@link TermOrder}
     */
    NavigableMap<String, Term> terms() {
        return this.terms;
    }

    /**
     * Returns the live documents.
     *
     * @return The documents by identifier
     */
    NavigableMap<DocId, Stored> docs() {
        return this.docs;
    }

    /**
     * Returns the live metadata.
     *
     * @return The metadata by key, ordered by {@link TermOrder}
     */
    NavigableMap<String, String> meta() {
        return this.meta;
    }

    /**
     * Returns the highest document identifier ever used.
     *
     * @return The last identifier
     */
    DocId last() {
        return this.last;
    }

    /**
     * Returns the total length of every document.
     *
     * @return The total length
     */
    long total() {
        return this.total;
    }

    /**
     * Tests whether positions are present.
     *
     * @return True when positions are present
     */
    boolean positional() {
        return this.positional;
    }

    /**
     * Returns a stored document.
     *
     * <p>An absent document raises a document-not-found error.</p>
     *
     * @param docid The document identifier
     * @return The stored document
     */
    Stored stored(final DocId docid) {
        final Stored found = this.docs.get(docid);
        if (found == null) {
            throw new DocNotFoundError(
                String.format("Document %s not found", docid)
            );
        }
        return found;
    }

    /**
     * Returns the statistics of a slot.
     *
     * @param slot The value slot number
     * @return The statistics, those of an unused slot when absent
     */
    SlotStats slot(final ValueNo slot) {
        return this.slots.getOrDefault(slot, new SlotStats());
    }

    /**
     * Adds a document under the next identifier.
     *
     * @param stored The document
     * @return The new identifier
     */
    DocId add(final Stored stored) {
        final DocId docid = this.last.next();
        this.index(docid, stored);
        return docid;
    }

    /**
     * Stores a document at an identifier, replacing any present.
     *
     * @param docid The document identifier
     * @param stored The document
     */
    void replace(final DocId docid, final Stored stored) {
        final Stored old = this.docs.get(docid);
        if (old != null) {
            this.unindex(docid, old);
        }
        this.index(docid, stored);
    }

    /**
     * Deletes a document.
     *
     * <p>An absent document raises a document-not-found error.</p>
     *
     * @param docid The document identifier
     */
    void delete(final DocId docid) {
        this.unindex(docid, this.stored(docid));
        if (this.docs.isEmpty()) {
            this.positional = false;
        }
    }

    /**
     * Lists the documents indexed by a term.
     *
     * @param term The term text
     * @return The identifiers in ascending order
     */
    List<DocId> indexed(final String term) {
        return new ArrayList<>(this.postings(term).keySet());
    }

    /**
     * Drops the contents and marks the store closed.
     */
    void close() {
        this.terms.clear();
        this.docs.clear();
        this.slots.clear();
        this.meta.clear();
        this.closed = true;
    }

    private Optional<Term> term(final String term) {
        return Optional.ofNullable(this.terms.get(term));
    }

    private void index(final DocId docid, final Stored stored) {
        for (final Map.Entry<String, Occurrence> entry
            : stored.terms().entrySet()) {
            this.terms.computeIfAbsent(entry.getKey(), key -> new Term())
                .post(docid, entry.getValue());
            if (!entry.getValue().positions().isEmpty()) {
                this.positional = true;
            }
        }
        for (final Map.Entry<ValueNo, String> entry
            : stored.values().entrySet()) {
            this.slots.computeIfAbsent(entry.getKey(), key -> new SlotStats())
                .add(entry.getValue());
        }
        this.total += stored.length().value();
        this.docs.put(docid, stored);
        if (docid.compareTo(this.last) > 0) {
            this.last = docid;
        }
    }

    private void unindex(final DocId docid, final Stored stored) {
        for (final Map.Entry<String, Occurrence> entry
            : stored.terms().entrySet()) {
            final Term term = this.terms.get(entry.getKey());
            term.unpost(docid, entry.getValue());
            if (term.postings().isEmpty()) {
                this.terms.remove(entry.getKey());
            }
        }
        for (final ValueNo slot : stored.values().keySet()) {
            this.slots.get(slot).remove();
        }
        this.total -= stored.length().value();
        this.docs.remove(docid);
    }
}
