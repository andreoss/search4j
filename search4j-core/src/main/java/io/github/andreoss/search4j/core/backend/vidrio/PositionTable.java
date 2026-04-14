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

/**
 * Lookups in a position table, with the position changes not yet written
 * read before the table.
 *
 * @since 0.1.0
 */
public final class PositionTable {

    /**
     * The table.
     */
    private final TableLookup table;

    /**
     * Changes not yet written to the table.
     */
    private final PendingPositions pending;

    /**
     * Creates the lookups over a table and its pending changes.
     *
     * @param table Table looked up
     * @param pending Changes not yet written to the table
     */
    PositionTable(final TableLookup table, final PendingPositions pending) {
        this.table = table;
        this.pending = pending;
    }

    /**
     * Opens the lookups over a table without pending changes.
     *
     * @param table Position table
     * @return Lookups over the table
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static PositionTable of(final TableLookup table) {
        return new PositionTable(table, new PendingPositions());
    }

    /**
     * Opens the positions of a term in a document.
     *
     * @param doc Document identifier
     * @param term Term text
     * @return List before its first position, empty when absent
     */
    public VidrioPositionList open(final DocId doc, final String term) {
        return VidrioPositionList.of(
            this.pending.get(doc, term).orElseGet(
                () -> this.table.get(PositionKeys.of(doc, term))
                    .orElseGet(() -> new byte[0])
            )
        );
    }

    /**
     * Number of positions of a term in a document.
     *
     * @param doc Document identifier
     * @param term Term text
     * @return Count, 0 when absent
     */
    public TermCount count(final DocId doc, final String term) {
        return this.open(doc, term).approxSize();
    }

    /**
     * Tests whether a term has positions in a document.
     *
     * @param doc Document identifier
     * @param term Term text
     * @return True when present
     */
    public boolean contains(final DocId doc, final String term) {
        return this.pending.get(doc, term)
            .map(tag -> tag.length > 0)
            .orElseGet(() -> this.table.contains(PositionKeys.of(doc, term)));
    }
}
