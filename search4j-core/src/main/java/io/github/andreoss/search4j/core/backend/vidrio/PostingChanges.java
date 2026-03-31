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

import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.Collections;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Pending changes to the posting list of one term: the postings added,
 * removed or changed, with the resulting change in frequencies.
 *
 * <p>The document lengths are changed through the same shape, their
 * frequency deltas unused.</p>
 *
 * @since 0.1.0
 */
final class PostingChanges {

    /**
     * New frequency per document, empty for a removed posting.
     */
    private final NavigableMap<DocId, Optional<TermCount>> changes;

    /**
     * Change in the term frequency.
     */
    private long terms;

    /**
     * Change in the collection frequency.
     */
    private long total;

    /**
     * Creates empty changes.
     */
    PostingChanges() {
        this.changes = new TreeMap<>();
    }

    /**
     * Adds a posting.
     *
     * @param doc Document identifier
     * @param wdf Frequency in the document
     */
    void add(final DocId doc, final TermCount wdf) {
        this.terms += 1L;
        this.total += wdf.value();
        this.changes.put(doc, Optional.of(wdf));
    }

    /**
     * Removes a posting.
     *
     * @param doc Document identifier
     * @param wdf Frequency the document had
     */
    void remove(final DocId doc, final TermCount wdf) {
        this.terms -= 1L;
        this.total -= wdf.value();
        this.changes.put(doc, Optional.empty());
    }

    /**
     * Changes the frequency of a posting.
     *
     * @param doc Document identifier
     * @param old Frequency the document had
     * @param wdf New frequency
     */
    void update(final DocId doc, final TermCount old, final TermCount wdf) {
        this.total += (long) wdf.value() - old.value();
        this.changes.put(doc, Optional.of(wdf));
    }

    /**
     * Change in the term frequency.
     *
     * @return Delta, possibly negative
     */
    long termDelta() {
        return this.terms;
    }

    /**
     * Change in the collection frequency.
     *
     * @return Delta, possibly negative
     */
    long collectionDelta() {
        return this.total;
    }

    /**
     * The changed postings in document order.
     *
     * @return New frequency per document, empty for a removed posting
     */
    NavigableMap<DocId, Optional<TermCount>> entries() {
        return Collections.unmodifiableNavigableMap(this.changes);
    }
}
