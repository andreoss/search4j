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

import java.util.Optional;

/**
 * Lookups in one table of a set, resolved on every call so a table opened
 * or reopened later is seen.
 *
 * @param tables Tables resolved on every call
 * @param type Table looked up
 * @since 0.1.0
 */
public record LiveLookup(TableSet tables, TableType type) implements TableLookup {

    @Override
    public TableCursor cursor() {
        return this.tables.lookup(this.type).cursor();
    }

    @Override
    public Optional<byte[]> get(final byte[] key) {
        return this.tables.lookup(this.type).get(key);
    }

    @Override
    public boolean contains(final byte[] key) {
        return this.tables.lookup(this.type).contains(key);
    }
}
