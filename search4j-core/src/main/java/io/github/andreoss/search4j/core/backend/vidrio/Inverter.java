/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file, the posting lists and the term
 * lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.TermFrequencies;
import io.github.andreoss.search4j.core.backend.TermOrder;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Buffers the posting, document length and position changes of the
 * documents added, replaced and deleted since the last flush, then merges
 * them into the tables term by term.
 *
 * @since 0.1.0
 */
public final class Inverter {

    /**
     * Changes by term, in term order.
     */
    private final NavigableMap<String, PostingChanges> postings;

    /**
     * Document length changes.
     */
    private final PendingLengths lengths;

    /**
     * Position list changes.
     */
    private final PendingPositions positions;

    /**
     * Creates an inverter without changes.
     */
    public Inverter() {
        this.postings = new TreeMap<>(TermOrder.INSTANCE);
        this.lengths = new PendingLengths();
        this.positions = new PendingPositions();
    }

    /**
     * Document length changes.
     *
     * @return Lengths pending
     */
    public PendingLengths lengths() {
        return this.lengths;
    }

    /**
     * Position list changes.
     *
     * @return Positions pending
     */
    public PendingPositions positions() {
        return this.positions;
    }

    /**
     * Adds a posting.
     *
     * @param doc Document identifier
     * @param term Term text
     * @param wdf Frequency in the document
     */
    public void addPosting(final DocId doc, final String term, final TermCount wdf) {
        this.changes(term).add(doc, wdf);
    }

    /**
     * Removes a posting.
     *
     * @param doc Document identifier
     * @param term Term text
     * @param wdf Frequency the document had
     */
    public void removePosting(
        final DocId doc, final String term, final TermCount wdf
    ) {
        this.changes(term).remove(doc, wdf);
    }

    /**
     * Changes the frequency of a posting.
     *
     * @param doc Document identifier
     * @param term Term text
     * @param wdfs Frequency the document had and the new one
     */
    public void updatePosting(
        final DocId doc, final String term, final WdfChange wdfs
    ) {
        this.changes(term).update(doc, wdfs.old(), wdfs.updated());
    }

    /**
     * Frequencies of a term with the pending changes applied.
     *
     * @param term Term text
     * @param stored Frequencies as stored
     * @return Frequencies as changed so far
     */
    public TermFrequencies frequencies(
        final String term, final TermFrequencies stored
    ) {
        TermFrequencies result = stored;
        final PostingChanges pending = this.postings.get(term);
        if (pending != null) {
            result = new TermFrequencies(
                TermCount.wrap(
                    stored.termFrequency().value() + pending.termDelta()
                ),
                TermCount.wrap(
                    stored.collection().value() + pending.collectionDelta()
                )
            );
        }
        return result;
    }

    /**
     * Merges the changes of one term into a posting list table.
     *
     * @param writer Merge into the table
     * @param term Term text
     */
    public void flushTerm(final PostingWriter writer, final String term) {
        final PostingChanges pending = this.postings.remove(term);
        if (pending != null) {
            writer.merge(term, pending);
        }
    }

    /**
     * Merges the changes of every term with a prefix into a posting list
     * table.
     *
     * @param writer Merge into the table
     * @param prefix Leading text, empty for every term
     */
    public void flushPrefix(final PostingWriter writer, final String prefix) {
        final Iterator<Map.Entry<String, PostingChanges>> iter =
            this.postings.tailMap(prefix, true).entrySet().iterator();
        while (iter.hasNext()) {
            final Map.Entry<String, PostingChanges> entry = iter.next();
            if (!entry.getKey().startsWith(prefix)) {
                break;
            }
            writer.merge(entry.getKey(), entry.getValue());
            iter.remove();
        }
    }

    /**
     * Merges the document lengths and every posting change into a posting
     * list table.
     *
     * @param writer Merge into the table
     */
    public void flush(final PostingWriter writer) {
        this.lengths.flush(writer);
        this.flushPrefix(writer, "");
    }

    /**
     * Forgets every change.
     */
    public void clear() {
        this.postings.clear();
        this.lengths.clear();
        this.positions.clear();
    }

    private PostingChanges changes(final String term) {
        return this.postings.computeIfAbsent(term, key -> new PostingChanges());
    }
}
