/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Error exceptions of the search engine.
 */
package io.github.andreoss.search4j.core.error;

/**
 * A timeout expired while communicating with a remote database.
 *
 * @since 0.1.0
 */
public final class NetworkTimeoutError extends NetworkError {

    /**
     * Serialisation identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates the error from a message.
     *
     * @param message Human-readable description of the failure
     */
    public NetworkTimeoutError(final String message) {
        this(message, "", 0);
    }

    /**
     * Creates the error from a message and context.
     *
     * @param message Human-readable description of the failure
     * @param context Machine-oriented detail, empty when none
     */
    public NetworkTimeoutError(final String message, final String context) {
        this(message, context, 0);
    }

    /**
     * Creates the error from a message and a system error number.
     *
     * @param message Human-readable description of the failure
     * @param errno System error number, zero when none
     */
    public NetworkTimeoutError(final String message, final int errno) {
        this(message, "", errno);
    }

    /**
     * Creates the error from a message, context and a system error number.
     *
     * @param message Human-readable description of the failure
     * @param context Machine-oriented detail, empty when none
     * @param errno System error number, zero when none
     */
    public NetworkTimeoutError(
        final String message,
        final String context,
        final int errno
    ) {
        super(message, context, errno);
    }
}
