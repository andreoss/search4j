/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.common.Strings;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The weight of a subquery multiplied by a factor.
 *
 * @param factor Non-negative scale factor
 * @param scaled Subquery scaled, never a match-nothing query
 * @since 0.1.0
 */
record ScaleNode(double factor, Query scaled) implements Node {

    /**
     * Creates the node.
     *
     * @param factor Non-negative scale factor
     * @param scaled Subquery scaled, never a match-nothing query
     */
    ScaleNode {
        Objects.requireNonNull(scaled, "scaled");
        if (factor < 0.0) {
            throw new InvalidArgumentError("SCALE_WEIGHT requires factor >= 0");
        }
    }

    @Override
    public QueryOp type() {
        return QueryOp.SCALE_WEIGHT;
    }

    @Override
    public String describe() {
        return String.format(
            "%s * %s", Strings.decimal(this.factor), this.scaled.node().describe()
        );
    }

    @Override
    public int subqueryCount() {
        return 1;
    }

    @Override
    public Query subquery(final int index) {
        return this.scaled;
    }

    @Override
    public TermCount length() {
        return this.scaled.length();
    }

    @Override
    public Stream<TermAt> terms() {
        return this.scaled.node().terms();
    }
}
