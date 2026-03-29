/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Error exceptions of the search engine.
 */
package io.github.andreoss.search4j.core.error;

/**
 * Base for exceptions indicating an error only detectable at runtime.
 *
 * <p>A subclass is thrown when the failure is caused by data or environment
 * rather than a programming mistake.</p>
 *
 * @since 0.1.0
 */
public abstract class RuntimeError extends SearchError {

    /**
     * Serialisation identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a runtime error.
     *
     * @param message The human-readable message
     * @param context Optional context text
     * @param errno Optional system error number
     */
    protected RuntimeError(
        final String message,
        final String context,
        final int errno
    ) {
        super(message, context, errno);
    }
}
