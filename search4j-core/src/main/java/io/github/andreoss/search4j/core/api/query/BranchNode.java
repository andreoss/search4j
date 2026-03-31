/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An operator over its subqueries.
 *
 * @param type The operator
 * @param subqueries Subqueries, none a match-nothing query
 * @param parameter Window of a positional operator or size of an elite set,
 *  zero for the other operators
 * @since 0.1.0
 */
record BranchNode(QueryOp type, List<Query> subqueries, int parameter)
    implements Node {

    /**
     * Creates the node.
     *
     * @param type The operator
     * @param subqueries Subqueries, none a match-nothing query
     * @param parameter Window of a positional operator or size of an elite
     *  set, zero for the other operators
     */
    BranchNode {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(subqueries, "subqueries");
    }

    @Override
    public String describe() {
        final String text;
        if (this.type == QueryOp.SYNONYM && this.subqueries.size() == 1) {
            text = String.format(
                "(SYNONYM %s)", this.subqueries.get(0).node().describe()
            );
        } else {
            final StringBuilder separator = new StringBuilder(16)
                .append(' ').append(this.type.name()).append(' ');
            if (this.parameter > 0) {
                separator.append(this.parameter).append(' ');
            }
            text = this.subqueries.stream()
                .map(sub -> sub.node().describe())
                .collect(Collectors.joining(separator, "(", ")"));
        }
        return text;
    }

    @Override
    public int subqueryCount() {
        return this.subqueries.size();
    }

    @Override
    public Query subquery(final int index) {
        return this.subqueries.get(index);
    }

    @Override
    public TermCount length() {
        return this.subqueries.stream()
            .map(Query::length)
            .reduce(TermCount.ZERO, TermCount::plus);
    }

    @Override
    public Stream<TermAt> terms() {
        return this.subqueries.stream().flatMap(sub -> sub.node().terms());
    }
}
