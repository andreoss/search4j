/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.QueryOp;

/**
 * The operators that may combine the terms of an expansion.
 *
 * @since 0.1.0
 */
final class Combiners {

    /**
     * Utility class, no instances.
     */
    private Combiners() {
    }

    /**
     * Checks a combiner.
     *
     * @param combiner Operator to check
     */
    static void check(final QueryOp combiner) {
        if (
            combiner != QueryOp.SYNONYM && combiner != QueryOp.MAX
                && combiner != QueryOp.OR
        ) {
            throw new InvalidArgumentError(
                "combiner must be SYNONYM or MAX or OR"
            );
        }
    }
}
