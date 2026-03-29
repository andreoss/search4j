/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Database check flag enumeration.
 */
package io.github.andreoss.search4j.core.types;

/**
 * Display and repair flags for a database consistency check.
 *
 * @since 0.1.0
 */
public enum CheckFlag {

    /**
     * Print the B-tree in short form.
     */
    SHORT_TREE(1),

    /**
     * Print the B-tree in full form.
     */
    FULL_TREE(2),

    /**
     * Print the free-list bitmap.
     */
    SHOW_FREELIST(4),

    /**
     * Print B-tree statistics.
     */
    SHOW_STATS(8),

    /**
     * Repair detected problems.
     */
    FIX(16);

    /**
     * The numeric value.
     */
    private final int code;

    /**
     * Creates the flag.
     *
     * @param code The numeric value
     */
    CheckFlag(final int code) {
        this.code = code;
    }

    /**
     * Returns the numeric value.
     *
     * @return The numeric value
     */
    public int code() {
        return this.code;
    }

    /**
     * Tests whether a check flag set carries this flag.
     *
     * @param flags The check flags
     * @return True when the flag is present
     */
    public boolean present(final int flags) {
        return (flags & this.code) != 0;
    }

    /**
     * Adds this flag to a check flag set.
     *
     * @param flags The check flags
     * @return The flags with this flag set
     */
    public int add(final int flags) {
        return flags | this.code;
    }
}
