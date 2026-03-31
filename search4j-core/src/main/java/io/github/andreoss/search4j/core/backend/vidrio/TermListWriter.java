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
import java.util.Map;

/**
 * Changes to the term lists of a table being written.
 *
 * @since 0.1.0
 */
public final class TermListWriter {

    /**
     * Table written.
     */
    private final TableWriter table;

    /**
     * Creates the changes over a table.
     *
     * @param table Table written
     */
    public TermListWriter(final TableWriter table) {
        this.table = table;
    }

    /**
     * Sets the term list of a document, replacing any present.
     *
     * @param doc Document identifier
     * @param terms Terms of one to 255 bytes with their frequency; the
     *  document length is their sum
     */
    public void set(final DocId doc, final Map<String, TermCount> terms) {
        this.table.add(TermListKeys.of(doc), TermListTag.encode(terms));
    }

    /**
     * Deletes the term list of a document.
     *
     * @param doc Document identifier
     * @return True when it was present
     */
    public boolean delete(final DocId doc) {
        return this.table.delete(TermListKeys.of(doc));
    }
}
