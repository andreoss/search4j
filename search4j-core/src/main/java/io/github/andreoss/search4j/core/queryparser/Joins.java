/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query parser grammar and parsing.
 */
package io.github.andreoss.search4j.core.queryparser;

import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.types.QueryOp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Combines parsed queries, appending to an existing conjunction,
 * disjunction or exclusive disjunction instead of nesting it.
 *
 * @since 0.1.0
 */
final class Joins {

    /**
     * Utility class, no instances.
     */
    private Joins() {
    }

    /**
     * Adds a query to an optional one with an operator.
     *
     * @param left Query built so far, absent when none
     * @param oper Operator
     * @param right Query added
     * @return Combined query
     */
    static Query add(
        final Optional<Query> left, final QueryOp oper, final Query right
    ) {
        final Query result;
        if (left.isEmpty()) {
            result = right;
        } else {
            result = Joins.join(left.get(), oper, right);
        }
        return result;
    }

    /**
     * Combines two queries with an operator.
     *
     * @param left First query
     * @param oper Operator
     * @param right Second query
     * @return Combined query
     */
    static Query join(final Query left, final QueryOp oper, final Query right) {
        final Query result;
        if (oper == QueryOp.AND && right.empty()) {
            result = right;
        } else if ((oper == QueryOp.OR || oper == QueryOp.XOR) && right.empty()) {
            result = left;
        } else if (Joins.flat(oper) && left.type() == oper) {
            final List<Query> parts = new ArrayList<>(left.subqueryCount() + 1);
            for (int idx = 0; idx < left.subqueryCount(); ++idx) {
                parts.add(left.subquery(idx));
            }
            parts.add(right);
            result = Query.of(oper, parts);
        } else {
            result = Query.of(oper, left, right);
        }
        return result;
    }

    private static boolean flat(final QueryOp oper) {
        return oper == QueryOp.AND || oper == QueryOp.OR || oper == QueryOp.XOR;
    }
}
