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
 * Lookups in a table: one committed, or one being written as changed so
 * far.
 *
 * @since 0.1.0
 */
public interface TableLookup {

    /**
     * Creates an unpositioned cursor over the entries.
     *
     * <p>Over a table being written the changed blocks are written first,
     * and the cursor does not see later changes.</p>
     *
     * @return Unpositioned cursor
     */
    TableCursor cursor();

    /**
     * Reads the tag of a key.
     *
     * @param key Key bytes; a key over {@link ItemKey#MAX_LENGTH} bytes is
     *  never present
     * @return Tag, reassembled and inflated, or empty when absent
     */
    Optional<byte[]> get(byte[] key);

    /**
     * Tests whether a key is present.
     *
     * @param key Key bytes
     * @return True when present
     */
    boolean contains(byte[] key);
}
