/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk table format definitions of the vidrio backend.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

/**
 * Number of entries in a table.
 *
 * @param value Non-negative count
 * @since 0.1.0
 */
public record EntryCount(long value) {

    /**
     * A table with no entries.
     */
    public static final EntryCount EMPTY = new EntryCount(0L);

    /**
     * Creates a count.
     *
     * @param value Non-negative count
     */
    public EntryCount {
        if (value < 0L) {
            throw new IllegalArgumentException(
                String.format("Entry count must not be negative: %d", value)
            );
        }
    }

    /**
     * Tests whether the table has no entries.
     *
     * @return True for zero
     */
    public boolean empty() {
        return this.value == 0L;
    }
}
