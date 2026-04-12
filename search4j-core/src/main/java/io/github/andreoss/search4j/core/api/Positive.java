/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.error.InvalidArgumentError;

/**
 * Scheme parameter that must be above zero.
 *
 * @param name Name of the parameter, for messages
 * @param value Value, above zero or not a number
 * @since 0.1.0
 */
record Positive(String name, double value) {

    /**
     * Rejects a value at or below zero.
     *
     * @param name Name of the parameter, for messages
     * @param value Value, above zero or not a number
     */
    Positive {
        if (value <= 0.0) {
            throw new InvalidArgumentError(
                String.format("Parameter %s is invalid", name)
            );
        }
    }
}
