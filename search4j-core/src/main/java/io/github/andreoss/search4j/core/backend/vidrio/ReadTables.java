/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file and the posting lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseError;
import java.util.EnumMap;
import java.util.Map;

/**
 * The tables of a database opened to read at the revision of its version
 * file.
 *
 * @since 0.1.0
 */
public final class ReadTables implements TableSet {

    /**
     * Tables by type.
     */
    private final Map<TableType, VidrioTable> tables;

    /**
     * Entry counts by type.
     */
    private final Map<TableType, EntryCount> counts;

    /**
     * Creates the set from its parts.
     *
     * @param tables Tables by type
     * @param counts Entry counts by type
     */
    private ReadTables(
        final Map<TableType, VidrioTable> tables,
        final Map<TableType, EntryCount> counts
    ) {
        this.tables = tables;
        this.counts = counts;
    }

    /**
     * Opens every table at the revision of a version file read.
     *
     * <p>Tables opened so far are closed when one fails.</p>
     *
     * @param files Table files of the directory
     * @param version Version file read
     * @return Tables
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static ReadTables open(
        final TableFiles files, final VersionFile version
    ) {
        final Map<TableType, VidrioTable> tables =
            new EnumMap<>(TableType.class);
        final Map<TableType, EntryCount> counts =
            new EnumMap<>(TableType.class);
        try {
            for (final TableType type : TableType.values()) {
                final TableState state = version.root(type);
                tables.put(type, files.read(type, state));
                counts.put(type, state.entries());
            }
        } catch (final DatabaseError ex) {
            for (final VidrioTable table : tables.values()) {
                table.close();
            }
            throw ex;
        }
        return new ReadTables(tables, counts);
    }

    @Override
    public TableLookup lookup(final TableType type) {
        return this.tables.get(type);
    }

    @Override
    public EntryCount entries(final TableType type) {
        return this.counts.get(type);
    }

    @Override
    public void close() {
        for (final VidrioTable table : this.tables.values()) {
            table.close();
        }
    }
}
