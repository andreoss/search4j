/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Wildcard expansion limit enumeration.
 */
package io.github.andreoss.search4j.core.types;

/**
 * Action taken when a wildcard expansion exceeds its limit.
 *
 * <p>Declared in the order of the expansion limits.</p>
 *
 * @since 0.1.0
 */
public enum WildcardLimit {

    /**
     * Raise an error when the limit is exceeded.
     */
    ERROR,

    /**
     * Expand to the first N terms in byte order.
     */
    FIRST,

    /**
     * Expand to the most frequent N terms.
     */
    MOST_FREQUENT
}
