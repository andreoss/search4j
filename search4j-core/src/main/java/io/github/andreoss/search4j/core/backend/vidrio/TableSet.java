/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file and the posting lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

/**
 * The tables of a database at one revision, as read or as changed so far.
 *
 * @since 0.1.0
 */
public interface TableSet extends AutoCloseable {

    /**
     * Lookups in a table.
     *
     * @param type Table
     * @return Lookups, over an empty table when the file is absent
     */
    TableLookup lookup(TableType type);

    /**
     * Number of entries of a table.
     *
     * @param type Table
     * @return Count
     */
    EntryCount entries(TableType type);

    @Override
    void close();
}
