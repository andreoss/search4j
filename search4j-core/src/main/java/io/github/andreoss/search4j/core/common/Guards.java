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
     * Ensures a condition holds, raising an error otherwise.
     *
     * @param condition The condition to verify
     * @param message The failure message
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
     * Ensures an object reference is not null.
     *
     * @param value The reference to check
     * @param name The name of the parameter
     * @param <T> The reference type
     * @return The non-null reference
     */
    public static <T> T notNull(final T value, final String name) {
        return Objects.requireNonNull(value, name);
    }
}
