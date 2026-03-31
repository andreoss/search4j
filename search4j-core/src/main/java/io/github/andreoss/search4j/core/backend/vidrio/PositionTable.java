/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file and the posting lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Lookups in a position table.
 *
 * @since 0.1.0
 */
public final class PositionTable {

    /**
     * The table.
     */
    private final TableLookup table;

    /**
     * Creates the lookups over a table.
     *
     * @param table The table
     */
    private PositionTable(final TableLookup table) {
        this.table = table;
    }

    /**
     * Opens the lookups over a table.
     *
     * @param table Position table
     * @return Lookups
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static PositionTable of(final TableLookup table) {
        return new PositionTable(table);
    }

    /**
     * Opens the positions of a term in a document.
     *
     * @param doc Document
     * @param term Term text
     * @return List before its first position, empty when absent
     */
    public VidrioPositionList open(final DocId doc, final String term) {
        return VidrioPositionList.of(
            this.table.get(PositionKeys.of(doc, term)).orElseGet(() -> new byte[0])
        );
    }

    /**
     * Number of positions of a term in a document.
     *
     * @param doc Document
     * @param term Term text
     * @return Count, 0 when absent
     */
    public TermCount count(final DocId doc, final String term) {
        return this.open(doc, term).approxSize();
    }

    /**
     * Tests whether a term has positions in a document.
     *
     * @param doc Document
     * @param term Term text
     * @return True when present
     */
    public boolean contains(final DocId doc, final String term) {
        return this.table.contains(PositionKeys.of(doc, term));
    }
}
