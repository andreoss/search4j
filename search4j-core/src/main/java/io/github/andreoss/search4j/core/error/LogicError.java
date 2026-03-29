/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Error exceptions of the search engine.
 */
package io.github.andreoss.search4j.core.error;

/**
 * Base for exceptions indicating an error in the program logic.
 *
 * <p>A subclass is thrown when a class invariant or a logical precondition is
 * violated.</p>
 *
 * @since 0.1.0
 */
public abstract class LogicError extends SearchError {

    /**
     * Serialisation identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a logic error.
     *
     * @param message The human-readable message
     * @param context Optional context text
     * @param errno Optional system error number
     */
    protected LogicError(
        final String message,
        final String context,
        final int errno
    ) {
        super(message, context, errno);
    }
}
