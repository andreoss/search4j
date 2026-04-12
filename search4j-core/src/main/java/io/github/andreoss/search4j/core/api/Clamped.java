/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

/**
 * Scheme parameter raised to zero when negative.
 *
 * @param value Value, zero or above, or not a number
 * @since 0.1.0
 */
record Clamped(double value) {

    /**
     * Raises a negative value to zero.
     *
     * @param value Value
     */
    Clamped {
        if (value < 0.0) {
            value = 0.0;
        }
    }
}
