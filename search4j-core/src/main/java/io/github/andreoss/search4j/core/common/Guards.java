/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Common utilities of the search engine.
 */
package io.github.andreoss.search4j.core.common;

import java.util.Objects;

/**
 * Precondition and invariant checks raised as an unchecked error.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class Guards {

    /**
     * Utility class, no instances.
     */
    private Guards() {
    }

    /**
     * Ensures a condition holds, raising an illegal-state error otherwise.
     *
     * @param condition Condition that must be true
     * @param message Message of the error raised when it is false
     */
    public static void require(
        final boolean condition,
        final String message
    ) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    /**
     * Ensures a reference is not null, raising a null-pointer error otherwise.
     *
     * @param value Reference to check
     * @param name Parameter name used as the error message
     * @param <T> Reference type
     * @return The same reference
     */
    public static <T> T notNull(final T value, final String name) {
        return Objects.requireNonNull(value, name);
    }
}
