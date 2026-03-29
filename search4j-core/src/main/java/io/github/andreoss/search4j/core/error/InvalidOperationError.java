/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Error exceptions of the search engine.
 */
package io.github.andreoss.search4j.core.error;

/**
 * An operation is invalid in the current state.
 *
 * @since 0.1.0
 */
public final class InvalidOperationError extends LogicError {

    /**
     * Serialisation identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates the error from a message.
     *
     * @param message The human-readable message
     */
    public InvalidOperationError(final String message) {
        this(message, "", 0);
    }

    /**
     * Creates the error from a message and context.
     *
     * @param message The human-readable message
     * @param context Optional context text
     */
    public InvalidOperationError(final String message, final String context) {
        this(message, context, 0);
    }

    /**
     * Creates the error from a message and a system error number.
     *
     * @param message The human-readable message
     * @param errno Optional system error number
     */
    public InvalidOperationError(final String message, final int errno) {
        this(message, "", errno);
    }

    /**
     * Creates the error from a message, context and a system error number.
     *
     * @param message The human-readable message
     * @param context Optional context text
     * @param errno Optional system error number
     */
    public InvalidOperationError(
        final String message,
        final String context,
        final int errno
    ) {
        super(message, context, errno);
    }
}
