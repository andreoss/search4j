/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Database opening action enumeration.
 */
package io.github.andreoss.search4j.core.types;

import java.util.Optional;

/**
 * Action taken when opening a database.
 *
 * <p>The action occupies the low two bits of an opening flag set.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.ProhibitPublicStaticMethods",
    "PMD.AvoidDirectAccessToStaticFields",
    "PMD.LongVariable"
})
public enum DatabaseAction {

    /**
     * Create the database if absent; this is the default action.
     */
    CREATE_OR_OPEN(0x00),

    /**
     * Create the database if absent, overwrite it if present.
     */
    CREATE_OR_OVERWRITE(0x01),

    /**
     * Create a new database; fail when it already exists.
     */
    CREATE(0x02),

    /**
     * Open an existing database; fail when it is absent.
     */
    OPEN(0x03);

    /**
     * Bit mask selecting the action from an opening flag set.
     */
    public static final int MASK = 0x03;

    /**
     * The numeric value.
     */
    private final int code;

    /**
     * Creates the action.
     *
     * @param code The numeric value
     */
    DatabaseAction(final int code) {
        this.code = code;
    }

    /**
     * Bits of the action within an opening flag set.
     *
     * @return Value under {@link #MASK}
     */
    public int code() {
        return this.code;
    }

    /**
     * Resolves the action carried in an opening flag set.
     *
     * @param flags Opening flag set to inspect
     * @return Action in the low two bits; present for every flag set
     */
    public static Optional<DatabaseAction> of(final int flags) {
        final int value = flags & MASK;
        Optional<DatabaseAction> found = Optional.empty();
        for (final DatabaseAction action : values()) {
            if (action.code == value) {
                found = Optional.of(action);
            }
        }
        return found;
    }
}
