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
     * @param message The human-readable message
     * @param context Optional context text
     * @param errno Optional system error number
     */
    protected DatabaseOpeningError(
        final String message,
        final String context,
        final int errno
    ) {
        super(message, context, errno);
    }
}
