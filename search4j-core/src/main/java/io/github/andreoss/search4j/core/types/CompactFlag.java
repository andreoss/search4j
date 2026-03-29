/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Database compaction flag enumeration.
 */
package io.github.andreoss.search4j.core.types;

/**
 * Flags for database compaction.
 *
 * @since 0.1.0
 */
public enum CompactFlag {

    /**
     * Keep the source document identifiers unchanged.
     */
    NO_RENUMBER(4),

    /**
     * Merge more than three databases in multiple passes.
     */
    MULTIPASS(8),

    /**
     * Produce a single-file database.
     */
    SINGLE_FILE(16);

    /**
     * The numeric value.
     */
    private final int code;

    /**
     * Creates the flag.
     *
     * @param code The numeric value
     */
    CompactFlag(final int code) {
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
     * Tests whether a compaction flag set carries this flag.
     *
     * @param flags The compaction flags
     * @return True when the flag is present
     */
    public boolean present(final int flags) {
        return (flags & this.code) != 0;
    }

    /**
     * Adds this flag to a compaction flag set.
     *
     * @param flags The compaction flags
     * @return The flags with this flag set
     */
    public int add(final int flags) {
        return flags | this.code;
    }
}
