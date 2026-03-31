/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file and the posting lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DocNotFoundError;
import io.github.andreoss.search4j.core.error.InvariantError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Document length changes buffered until the next flush.
 *
 * @since 0.1.0
 */
public final class PendingLengths {

    /**
     * New length per document, empty for a document removed.
     */
    private final Map<DocId, Optional<TermCount>> changes;

    /**
     * Creates empty changes.
     */
    public PendingLengths() {
        this.changes = new TreeMap<>();
    }

    /**
     * Sets the length of a document.
     *
     * @param doc Document
     * @param length Its length
     * @param added True when the document is added, so no length may be
     *  pending for it
     */
    public void set(final DocId doc, final TermCount length, final boolean added) {
        final Optional<TermCount> pending = this.changes.get(doc);
        if (added && pending != null && pending.isPresent()) {
            throw new InvariantError(
                String.format("Document %s already has a pending length", doc)
            );
        }
        this.changes.put(doc, Optional.of(length));
    }

    /**
     * Removes the length of a document.
     *
     * @param doc Document
     */
    public void delete(final DocId doc) {
        final Optional<TermCount> pending = this.changes.get(doc);
        if (pending != null && pending.isEmpty()) {
            throw new InvariantError(
                String.format("Document %s already pending removal", doc)
            );
        }
        this.changes.put(doc, Optional.empty());
    }

    /**
     * Length of a document as changed so far.
     *
     * <p>A document pending removal raises a document-not-found error.</p>
     *
     * @param doc Document
     * @return Length, empty when no change is pending
     */
    public Optional<TermCount> get(final DocId doc) {
        final Optional<TermCount> pending = this.changes.get(doc);
        if (pending != null && pending.isEmpty()) {
            throw new DocNotFoundError(
                String.format("Document not found: %s", doc)
            );
        }
        return Optional.ofNullable(pending).flatMap(found -> found);
    }

    /**
     * Merges the changes into a posting list table and forgets them.
     *
     * @param writer Merge into the table
     */
    public void flush(final PostingWriter writer) {
        final PostingChanges merged = new PostingChanges();
        for (final Map.Entry<DocId, Optional<TermCount>> entry
            : this.changes.entrySet()) {
            if (entry.getValue().isPresent()) {
                merged.add(entry.getKey(), entry.getValue().get());
            } else {
                merged.remove(entry.getKey(), TermCount.ZERO);
            }
        }
        writer.mergeLengths(merged);
        this.changes.clear();
    }

    /**
     * Forgets the changes.
     */
    public void clear() {
        this.changes.clear();
    }
}
