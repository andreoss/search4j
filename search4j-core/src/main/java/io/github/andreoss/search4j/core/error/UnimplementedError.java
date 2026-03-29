/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Error exceptions of the search engine.
 */
package io.github.andreoss.search4j.core.error;

/**
 * A requested feature is not implemented.
 *
 * @since 0.1.0
 */
public final class UnimplementedError extends LogicError {

    /**
     * Serialisation identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates the error from a message.
     *
     * @param message The human-readable message
     */
    public UnimplementedError(final String message) {
        this(message, "", 0);
    }

    /**
     * Creates the error from a message and context.
     *
     * @param message The human-readable message
     * @param context Optional context text
     */
    public UnimplementedError(final String message, final String context) {
        this(message, context, 0);
    }

    /**
     * Creates the error from a message and a system error number.
     *
     * @param message The human-readable message
     * @param errno Optional system error number
     */
    public UnimplementedError(final String message, final int errno) {
        this(message, "", errno);
    }

    /**
     * Creates the error from a message, context and a system error number.
     *
     * @param message The human-readable message
     * @param context Optional context text
     * @param errno Optional system error number
     */
    public UnimplementedError(
        final String message,
        final String context,
        final int errno
    ) {
        super(message, context, errno);
    }
}
