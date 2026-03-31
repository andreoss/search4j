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
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.List;

/**
 * Changes to the position lists of a table being written.
 *
 * @since 0.1.0
 */
public final class PositionWriter {

    /**
     * Table written.
     */
    private final TableWriter table;

    /**
     * Creates the changes over a table.
     *
     * @param table Table written
     */
    public PositionWriter(final TableWriter table) {
        this.table = table;
    }

    /**
     * Sets the positions of a term in a document, replacing any present.
     *
     * @param doc Document identifier
     * @param term Term text
     * @param positions Strictly ascending positions, at least one
     */
    public void set(
        final DocId doc, final String term, final List<TermPos> positions
    ) {
        this.table.add(PositionKeys.of(doc, term), PositionTag.encode(positions));
    }

    /**
     * Deletes the positions of a term in a document.
     *
     * @param doc Document identifier
     * @param term Term text
     * @return True when they were present
     */
    public boolean delete(final DocId doc, final String term) {
        return this.table.delete(PositionKeys.of(doc, term));
    }
}
