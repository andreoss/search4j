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
 * Changes to the user metadata of a posting list table being written.
 *
 * @since 0.1.0
 */
public final class MetadataWriter {

    /**
     * Table written.
     */
    private final TableWriter table;

    /**
     * Creates the changes over a table.
     *
     * @param table Table written
     */
    public MetadataWriter(final TableWriter table) {
        this.table = table;
    }

    /**
     * Sets a metadata item, removing it when the value is empty.
     *
     * @param key Key text, not empty
     * @param value Value text
     */
    public void set(final String key, final String value) {
        final byte[] name = MetadataKeys.of(key);
        if (value.isEmpty()) {
            this.table.delete(name);
        } else {
            this.table.add(name, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
