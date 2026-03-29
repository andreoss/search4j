/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Error exceptions of the search engine.
 */
package io.github.andreoss.search4j.core.error;

/**
 * Base for exceptions raised while opening a database.
 *
 * @since 0.1.0
 */
public abstract class DatabaseOpeningError extends DatabaseError {

    /**
     * Serialisation identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a database opening error.
     *
     * @param message Human-readable description of the failure
     * @param context Machine-oriented detail, empty when none
     * @param errno System error number, zero when none
     */
    protected DatabaseOpeningError(
        final String message,
        final String context,
        final int errno
    ) {
        super(message, context, errno);
    }
}
