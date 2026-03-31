/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Position lists written to a table in memory and committed to read.
 *
 * @since 0.1.0
 */
final class Positions {

    /**
     * Blocks written.
     */
    private final MemoryBlocks blocks;

    /**
     * Table written.
     */
    private final TableWriter table;

    /**
     * Changes to the position lists.
     */
    private final PositionWriter writer;

    /**
     * Revision last committed.
     */
    private long revision;

    /**
     * Creates the lists from their parts.
     *
     * @param blocks Blocks written
     * @param table Table written
     */
    private Positions(final MemoryBlocks blocks, final TableWriter table) {
        this.blocks = blocks;
        this.table = table;
        this.writer = new PositionWriter(table);
        this.revision = 0L;
    }

    /**
     * Creates an empty table.
     *
     * @return Lists
     */
    static Positions of() {
        final MemoryBlocks blocks =
            new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        return new Positions(blocks, TableWriter.create(blocks, 0));
    }

    /**
     * An empty committed table.
     *
     * @return Lookups without positions
     */
    static PositionTable none() {
        return Positions.of().commit();
    }

    /**
     * Positions as a list.
     *
     * @param values Position numbers
     * @return Positions in the order given
     */
    static List<TermPos> list(final int... values) {
        return Arrays.stream(values).mapToObj(TermPos::new).toList();
    }

    /**
     * Sets the positions of a term in a document.
     *
     * @param doc Document number
     * @param term Term
     * @param list Ascending positions
     */
    void set(final int doc, final String term, final List<TermPos> list) {
        this.writer.set(DocId.of(doc), term, list);
    }

    /**
     * Deletes the positions of a term in a document.
     *
     * @param doc Document number
     * @param term Term
     * @return True when they were present
     */
    boolean delete(final int doc, final String term) {
        return this.writer.delete(DocId.of(doc), term);
    }

    /**
     * Table written.
     *
     * @return Writer
     */
    TableWriter writer() {
        return this.table;
    }

    /**
     * Commits the table as the next revision and opens it to read.
     *
     * @return Lookups over the revision
     */
    PositionTable commit() {
        this.revision = this.revision + 1L;
        return PositionTable.of(
            VidrioTable.over(
                this.blocks,
                this.table.commit(new RevisionNumber(this.revision)).root()
            )
        );
    }
}
