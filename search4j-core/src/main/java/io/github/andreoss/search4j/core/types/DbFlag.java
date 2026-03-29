/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Writable database opening flag enumeration.
 */
package io.github.andreoss.search4j.core.types;

/**
 * Opening flags for a writable database.
 *
 * <p>These flags sit above the action and backend bits. Combine them with
 * {@link #add(int)} and test them with {@link #present(int)}.</p>
 *
 * @since 0.1.0
 */
public enum DbFlag {

    /**
     * Do not request that changes reach durable storage.
     */
    NO_SYNC(0x04),

    /**
     * Try to flush data past the drive write-back cache.
     */
    FULL_SYNC(0x08),

    /**
     * Update the database in place instead of copy-on-write.
     */
    DANGEROUS(0x10),

    /**
     * Omit the termlist table when creating a database.
     */
    NO_TERMLIST(0x20),

    /**
     * Wait for an existing writer lock instead of failing.
     */
    RETRY_LOCK(0x40);

    /**
     * The numeric value.
     */
    private final int code;

    /**
     * Creates the flag.
     *
     * @param code The numeric value
     */
    DbFlag(final int code) {
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
     * Tests whether an opening flag set carries this flag.
     *
     * @param flags The opening flags
     * @return True when the flag is present
     */
    public boolean present(final int flags) {
        return (flags & this.code) != 0;
    }

    /**
     * Adds this flag to an opening flag set.
     *
     * @param flags The opening flags
     * @return The flags with this flag set
     */
    public int add(final int flags) {
        return flags | this.code;
    }
}
