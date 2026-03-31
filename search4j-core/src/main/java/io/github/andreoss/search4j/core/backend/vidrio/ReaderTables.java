/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file and the posting lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import java.util.function.Supplier;

/**
 * The lookups a read side works through, over the tables of a set.
 *
 * @param postings Lookups in the posting list table
 * @param termlists Lookups in the term list table
 * @param positions Lookups in the position table
 * @param values Lookups of the values
 * @param metadata Lookups of the metadata
 * @since 0.1.0
 */
public record ReaderTables(
    Supplier<PostingTable> postings, TermListTable termlists,
    PositionTable positions, ValueTable values, MetadataTable metadata
) {

    /**
     * Opens the lookups over a set of tables, each resolved on every call.
     *
     * @param tables The tables
     * @param sources Posting list lookups and term frequencies
     * @return Lookups
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static ReaderTables of(
        final TableSet tables, final PostingSources sources
    ) {
        final PositionTable positions = PositionTable.of(
            new LiveLookup(tables, TableType.POSITION)
        );
        return new ReaderTables(
            sources.postings(),
            TermListTable.of(
                new LiveLookup(tables, TableType.TERMLIST), sources.freqs(),
                positions
            ),
            positions,
            ValueTable.of(
                new LiveLookup(tables, TableType.POSTLIST),
                new LiveLookup(tables, TableType.TERMLIST)
            ),
            MetadataTable.of(new LiveLookup(tables, TableType.POSTLIST))
        );
    }
}
