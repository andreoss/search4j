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
import io.github.andreoss.search4j.core.types.DocId;
import java.util.Optional;

/**
 * Lookups in a committed term list table.
 *
 * @since 0.1.0
 */
public final class TermListTable {

    /**
     * The table.
     */
    private final VidrioTable table;

    /**
     * Posting list table the term frequencies come from.
     */
    private final PostingTable postings;

    /**
     * Position table the positions come from.
     */
    private final PositionTable positions;

    /**
     * Creates the lookups from their parts.
     *
     * @param table The table
     * @param postings Posting list table the term frequencies come from
     * @param positions Position table the positions come from
     */
    private TermListTable(
        final VidrioTable table, final PostingTable postings,
        final PositionTable positions
    ) {
        this.table = table;
        this.postings = postings;
        this.positions = positions;
    }

    /**
     * Opens the lookups over a table.
     *
     * @param table Term list table
     * @param postings Posting list table the term frequencies come from
     * @param positions Position table the positions come from
     * @return Lookups
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static TermListTable of(
        final VidrioTable table, final PostingTable postings,
        final PositionTable positions
    ) {
        return new TermListTable(table, postings, positions);
    }

    /**
     * Opens the term list of a document.
     *
     * @param doc Document
     * @return List before its first term, a not-found error when absent
     */
    public VidrioTermList open(final DocId doc) {
        return this.find(doc).orElseThrow(
            () -> new DocNotFoundError(
                String.format("No term list for document %s", doc)
            )
        );
    }

    /**
     * Finds the term list of a document.
     *
     * @param doc Document
     * @return List before its first term, or empty when absent
     */
    public Optional<VidrioTermList> find(final DocId doc) {
        return this.table.get(TermListKeys.of(doc)).map(
            tag -> VidrioTermList.over(tag, doc, this)
        );
    }

    /**
     * Tests whether a document has a term list.
     *
     * @param doc Document
     * @return True when present
     */
    public boolean contains(final DocId doc) {
        return this.table.contains(TermListKeys.of(doc));
    }

    /**
     * Posting list table the term frequencies come from.
     *
     * @return Lookups
     */
    PostingTable postings() {
        return this.postings;
    }

    /**
     * Position table the positions come from.
     *
     * @return Lookups
     */
    PositionTable positions() {
        return this.positions;
    }
}
