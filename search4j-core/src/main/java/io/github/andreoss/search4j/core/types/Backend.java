/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Database backend enumeration.
 */
package io.github.andreoss.search4j.core.types;

import java.util.Optional;

/**
 * Backend selected when opening a database.
 *
 * <p>The backend occupies bits 8 to 10 of an opening flag set.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings({"PMD.ProhibitPublicStaticMethods", "PMD.AvoidDirectAccessToStaticFields"})
public enum Backend {

    /**
     * The Vidrio backend.
     */
    VIDRIO(0x100),

    /**
     * The legacy chert backend.
     */
    CHERT(0x200),

    /**
     * A stub database file naming other databases.
     */
    STUB(0x300),

    /**
     * The in-memory backend.
     */
    IN_MEMORY(0x400);

    /**
     * Bit mask selecting the backend from an opening flag set.
     */
    public static final int MASK = 0x700;

    /**
     * The numeric value.
     */
    private final int code;

    /**
     * Creates the backend.
     *
     * @param code The numeric value
     */
    Backend(final int code) {
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
     * Tests whether an opening flag set names this backend.
     *
     * @param flags The opening flags
     * @return True when the backend bits equal this backend's value
     */
    public boolean selected(final int flags) {
        return (flags & MASK) == this.code;
    }

    /**
     * Adds this backend to an opening flag set.
     *
     * @param flags The opening flags
     * @return The flags naming this backend
     */
    public int add(final int flags) {
        return (flags & ~MASK) | this.code;
    }

    /**
     * Resolves the backend carried in an opening flag set.
     *
     * @param flags The opening flags
     * @return The backend, empty when the flag set names no backend
     */
    public static Optional<Backend> of(final int flags) {
        final int value = flags & MASK;
        Optional<Backend> found = Optional.empty();
        for (final Backend backend : values()) {
            if (backend.code == value) {
                found = Optional.of(backend);
            }
        }
        return found;
    }
}
