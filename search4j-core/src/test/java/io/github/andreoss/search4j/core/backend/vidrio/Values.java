/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Values and metadata written to tables in memory and committed to read.
 *
 * @since 0.1.0
 */
final class Values {

    /**
     * Blocks of the posting list table.
     */
    private final MemoryBlocks pblocks;

    /**
     * Blocks of the term list table.
     */
    private final MemoryBlocks tblocks;

    /**
     * Posting list table written.
     */
    private final TableWriter postings;

    /**
     * Term list table written.
     */
    private final TableWriter termlists;

    /**
     * Changes to the values.
     */
    private final ValueWriter writer;

    /**
     * Revision last committed.
     */
    private long revision;

    /**
     * Posting list table last committed.
     */
    private VidrioTable committed;

    /**
     * Creates the tables from their parts.
     *
     * @param pblocks Blocks of the posting list table
     * @param tblocks Blocks of the term list table
     * @param postings Posting list table written
     * @param termlists Term list table written
     */
    private Values(
        final MemoryBlocks pblocks, final MemoryBlocks tblocks,
        final TableWriter postings, final TableWriter termlists
    ) {
        this.pblocks = pblocks;
        this.tblocks = tblocks;
        this.postings = postings;
        this.termlists = termlists;
        this.writer = new ValueWriter(postings, termlists);
        this.revision = 0L;
    }

    /**
     * Creates empty tables.
     *
     * @return Tables
     */
    static Values of() {
        final MemoryBlocks pblocks = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        final MemoryBlocks tblocks = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        return new Values(
            pblocks, tblocks, TableWriter.create(pblocks, 0),
            TableWriter.create(tblocks, 18)
        );
    }

    /**
     * Values by slot.
     *
     * @param pairs Slot numbers and values, alternating
     * @return Values in slot order
     */
    static SortedMap<ValueNo, String> map(final Object... pairs) {
        final SortedMap<ValueNo, String> result = new TreeMap<>();
        for (int idx = 0; idx < pairs.length; idx += 2) {
            result.put(new ValueNo((Integer) pairs[idx]), (String) pairs[idx + 1]);
        }
        return result;
    }

    /**
     * Entries of a table as key and tag in hex.
     *
     * @param table Table read
     * @return Entries in key order
     */
    static List<String> dump(final TableLookup table) {
        final TableCursor cursor = table.cursor();
        cursor.rewind();
        final List<String> result = new ArrayList<>(0);
        while (cursor.next()) {
            result.add(
                String.format(
                    "%s=%s", cursor.key(), HexFormat.of().formatHex(cursor.tag())
                )
            );
        }
        return result;
    }

    /**
     * Changes to the values.
     *
     * @return Writer
     */
    ValueWriter writer() {
        return this.writer;
    }

    /**
     * Changes to the metadata.
     *
     * @return Writer over the posting list table
     */
    MetadataWriter metadata() {
        return new MetadataWriter(this.postings);
    }

    /**
     * Posting list table written.
     *
     * @return Writer
     */
    TableWriter postings() {
        return this.postings;
    }

    /**
     * Term list table written.
     *
     * @return Writer
     */
    TableWriter termlists() {
        return this.termlists;
    }

    /**
     * Posting list table last committed.
     *
     * @return Table
     */
    VidrioTable table() {
        return this.committed;
    }

    /**
     * First documents of the chunks of a slot in the table last committed.
     *
     * @param slot Slot number
     * @return Chunk starts in order
     */
    List<DocId> chunks(final int slot) {
        final List<DocId> result = new ArrayList<>(0);
        final ValueNo number = new ValueNo(slot);
        final TableCursor cursor = this.committed.cursor();
        cursor.seekAtLeast(ValueKeys.chunk(number, DocId.of(1)));
        boolean more = !cursor.afterEnd();
        while (more) {
            more = ValueKeys.chunkStart(number, cursor.key())
                .map(result::add).orElse(false) && cursor.next();
        }
        return result;
    }

    /**
     * Writes the statistics, merges the changes, commits both tables as the next revision and
     * opens them to read.
     *
     * @return Lookups over the revision
     */
    ValueTable commit() {
        this.writer.writeStats();
        this.writer.merge();
        this.revision = this.revision + 1L;
        final RevisionNumber number = new RevisionNumber(this.revision);
        this.committed = VidrioTable.over(
            this.pblocks, this.postings.commit(number).root()
        );
        return ValueTable.of(
            this.committed,
            VidrioTable.over(this.tblocks, this.termlists.commit(number).root())
        );
    }
}
