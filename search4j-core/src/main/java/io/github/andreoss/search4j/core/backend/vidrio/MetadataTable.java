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

import java.nio.charset.StandardCharsets;

/**
 * Lookups of the user metadata in a posting list table.
 *
 * @since 0.1.0
 */
public final class MetadataTable {

    /**
     * The table.
     */
    private final TableLookup table;

    /**
     * Creates the lookups over a table.
     *
     * @param table Table looked up
     */
    private MetadataTable(final TableLookup table) {
        this.table = table;
    }

    /**
     * Opens the lookups over a table.
     *
     * @param table Posting list table
     * @return Lookups over the table
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static MetadataTable of(final TableLookup table) {
        return new MetadataTable(table);
    }

    /**
     * Value of a metadata item.
     *
     * @param key Key text, not empty
     * @return Value, empty when absent
     */
    public String get(final String key) {
        return new String(
            this.table.get(MetadataKeys.of(key)).orElseGet(() -> new byte[0]),
            StandardCharsets.UTF_8
        );
    }

    /**
     * Opens the metadata keys starting with a prefix.
     *
     * @param prefix Leading text, empty for every key
     * @return Keys in ascending order, before the first
     */
    public VidrioMetadataKeyList keys(final String prefix) {
        return VidrioMetadataKeyList.open(this.table, prefix);
    }
}
