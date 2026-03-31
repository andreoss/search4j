/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.common.Strings;
import java.util.Objects;

/**
 * A weighted synonym: an unweighted union weighted as one term.
 *
 * @param factor Weight factor, not zero
 * @param union Unweighted union of the subqueries
 * @since 0.1.0
 */
record SynonymPlan(double factor, Plan union) implements Plan {

    /**
     * Creates the plan.
     *
     * @param factor Weight factor, not zero
     * @param union Unweighted union of the subqueries
     */
    SynonymPlan {
        Objects.requireNonNull(union, "union");
    }

    @Override
    public String describe() {
        return String.format(
            "SYNONYM:%s(%s)", Strings.decimal(this.factor), this.union.describe()
        );
    }
}
