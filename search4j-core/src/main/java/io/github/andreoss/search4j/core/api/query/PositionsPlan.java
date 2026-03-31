/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import java.util.Objects;

/**
 * A subquery of a positional operator other than a term, whose positions
 * are merged.
 *
 * @param inner Plan of the subquery
 * @since 0.1.0
 */
record PositionsPlan(Plan inner) implements Plan {

    /**
     * Creates the plan.
     *
     * @param inner Plan of the subquery
     */
    PositionsPlan {
        Objects.requireNonNull(inner, "inner");
    }

    @Override
    public String describe() {
        return String.format("POS(%s)", this.inner.describe());
    }
}
