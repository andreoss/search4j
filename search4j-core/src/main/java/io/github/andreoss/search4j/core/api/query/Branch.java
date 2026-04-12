/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.UnimplementedError;
import io.github.andreoss.search4j.core.types.QueryOp;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * An operator node under construction: subqueries are added with the
 * reference simplifications, then the finished node is taken.
 *
 * <p>Match-nothing subqueries are absorbed by the and-like operators,
 * dropped by the or-like ones, dropped on the right of a difference or an
 * and-maybe and absorb the rest on their left; a node left with one
 * subquery collapses to it.</p>
 *
 * @since 0.1.0
 */
final class Branch {

    /**
     * The operator.
     */
    private final QueryOp type;

    /**
     * How the operator treats match-nothing subqueries.
     */
    private final Branch.Kind kind;

    /**
     * Window or set size as given, zero for the default.
     */
    private final int parameter;

    /**
     * Subqueries added so far.
     */
    private final List<Query> subqueries;

    /**
     * Creates the branch.
     *
     * @param type The operator
     * @param kind How the operator treats match-nothing subqueries
     * @param parameter Window or set size as given, zero for the default
     */
    private Branch(
        final QueryOp type, final Branch.Kind kind, final int parameter
    ) {
        this.type = type;
        this.kind = kind;
        this.parameter = parameter;
        this.subqueries = new ArrayList<>(2);
    }

    /**
     * Starts a branch.
     *
     * @param type Operator taking subqueries
     * @param parameter Window of a positional operator or size of an elite
     *  set, zero for the default; invalid with any other operator
     * @return Empty branch
     */
    static Branch of(final QueryOp type, final int parameter) {
        if (
            parameter != 0 && type != QueryOp.NEAR && type != QueryOp.PHRASE
                && type != QueryOp.ELITE_SET
        ) {
            throw new InvalidArgumentError(
                "parameter only valid with NEAR, PHRASE or ELITE_SET"
            );
        }
        return new Branch(
            type,
            Branch.Kind.of(type).orElseThrow(
                () -> new InvalidArgumentError(
                    "op not valid with a list of subqueries"
                )
            ),
            parameter
        );
    }

    /**
     * Combines two subqueries.
     *
     * @param type Operator taking subqueries
     * @param left First subquery
     * @param right Second subquery
     * @return Finished query
     */
    static Query pair(final QueryOp type, final Query left, final Query right) {
        final Branch branch = Branch.of(type, 0);
        final boolean positional = Branch.positional(type);
        branch.add(left, positional);
        branch.add(right, positional);
        return branch.done();
    }

    /**
     * Combines the subqueries of an iterator.
     *
     * @param type Operator taking subqueries
     * @param subqueries Subqueries; none gives a match-nothing query without
     *  any check
     * @param parameter Window or set size, zero for the default
     * @return Finished query
     */
    static Query list(
        final QueryOp type, final Iterator<Query> subqueries,
        final int parameter
    ) {
        final Query result;
        if (subqueries.hasNext()) {
            final Branch branch = Branch.of(type, parameter);
            final boolean positional = Branch.positional(type);
            while (subqueries.hasNext()) {
                branch.add(subqueries.next(), positional);
            }
            result = branch.done();
        } else {
            result = Query.MATCH_NOTHING;
        }
        return result;
    }

    /**
     * Combines terms; a positional operator takes them as they are.
     *
     * @param type Operator taking subqueries
     * @param terms Terms; none gives a match-nothing query without any check
     * @param parameter Window or set size, zero for the default
     * @return Finished query
     */
    static Query terms(
        final QueryOp type, final Iterator<String> terms, final int parameter
    ) {
        final Query result;
        if (terms.hasNext()) {
            final Branch branch = Branch.of(type, parameter);
            while (terms.hasNext()) {
                branch.add(Query.term(terms.next()), false);
            }
            result = branch.done();
        } else {
            result = Query.MATCH_NOTHING;
        }
        return result;
    }

    /**
     * Adds a subquery.
     *
     * @param subquery Subquery to add
     * @param positional Whether the operator needs positions: then only
     *  terms and unions are accepted, and queries without positions become
     *  match-nothing queries
     */
    void add(final Query subquery, final boolean positional) {
        final Query added;
        if (positional) {
            added = Branch.positioned(subquery);
        } else {
            added = subquery;
        }
        this.kind.add(this.subqueries, added);
    }

    /**
     * Finishes the branch.
     *
     * @return The query: match-nothing without subqueries, the single
     *  subquery when one is left, otherwise the operator node
     */
    Query done() {
        final Query result;
        if (this.subqueries.isEmpty()) {
            result = Query.MATCH_NOTHING;
        } else if (this.subqueries.size() == 1) {
            result = this.single();
        } else {
            result = new Query(
                new BranchNode(
                    this.type, List.copyOf(this.subqueries), this.sized()
                )
            );
        }
        return result;
    }

    private Query single() {
        final Query sub = this.subqueries.get(0);
        final Query result;
        if (this.type == QueryOp.SYNONYM) {
            result = Branch.synonymOf(sub);
        } else {
            result = sub;
        }
        return result;
    }

    private int sized() {
        final int size;
        if (this.parameter == 0 && Branch.positional(this.type)) {
            size = this.subqueries.size();
        } else if (this.parameter == 0 && this.type == QueryOp.ELITE_SET) {
            size = 10;
        } else {
            size = this.parameter;
        }
        return size;
    }

    private static Query synonymOf(final Query sub) {
        return switch (sub.type()) {
            case LEAF_TERM, LEAF_MATCH_ALL, SYNONYM -> sub;
            case WILDCARD -> new Query(
                new WildcardNode(
                    ((WildcardNode) sub.node()).spec()
                        .withCombiner(QueryOp.SYNONYM)
                )
            );
            case EDIT_DISTANCE -> new Query(
                new EditNode(
                    ((EditNode) sub.node()).spec().withCombiner(QueryOp.SYNONYM)
                )
            );
            default -> new Query(
                new BranchNode(QueryOp.SYNONYM, List.of(sub), 0)
            );
        };
    }

    private static Query positioned(final Query subquery) {
        return switch (subquery.type()) {
            case LEAF_TERM, OR -> subquery;
            case LEAF_POSTING_SOURCE, LEAF_MATCH_ALL, LEAF_MATCH_NOTHING ->
                Query.MATCH_NOTHING;
            default -> throw new UnimplementedError(
                "NEAR and PHRASE only currently support leaf subqueries"
            );
        };
    }

    private static boolean positional(final QueryOp type) {
        return type == QueryOp.NEAR || type == QueryOp.PHRASE;
    }

    /**
     * How an operator treats match-nothing subqueries.
     *
     * @since 0.1.0
     */
    private enum Kind {

        /**
         * Intersection-like: a match-nothing subquery absorbs the rest.
         */
        AND_LIKE {
            @Override
            void add(final List<Query> items, final Query added) {
                if (!Branch.Kind.absorbed(items)) {
                    if (added.empty()) {
                        items.clear();
                    }
                    items.add(added);
                }
            }
        },

        /**
         * Union-like: match-nothing subqueries are dropped.
         */
        OR_LIKE {
            @Override
            void add(final List<Query> items, final Query added) {
                if (!added.empty()) {
                    items.add(added);
                }
            }
        },

        /**
         * Difference: the left side decides, match-nothing on the right is
         * dropped and a scaled right side loses its scaling.
         */
        AND_NOT {
            @Override
            void add(final List<Query> items, final Query added) {
                if (items.isEmpty()) {
                    items.add(added);
                } else if (!Branch.Kind.absorbed(items) && !added.empty()) {
                    if (added.type() == QueryOp.SCALE_WEIGHT) {
                        items.add(added.subquery(0));
                    } else {
                        items.add(added);
                    }
                }
            }
        },

        /**
         * And-maybe: the left side decides, match-nothing on the right is
         * dropped.
         */
        AND_MAYBE {
            @Override
            void add(final List<Query> items, final Query added) {
                if (items.isEmpty()) {
                    items.add(added);
                } else if (!Branch.Kind.absorbed(items) && !added.empty()) {
                    items.add(added);
                }
            }
        };

        /**
         * Adds a subquery under the policy.
         *
         * @param items Subqueries so far
         * @param added Subquery to add
         */
        abstract void add(List<Query> items, Query added);

        /**
         * Policy of an operator.
         *
         * @param type The operator
         * @return Policy, empty for an operator without subqueries
         */
        static Optional<Branch.Kind> of(final QueryOp type) {
            return Optional.ofNullable(
                switch (type) {
                    case AND, FILTER, NEAR, PHRASE -> Branch.Kind.AND_LIKE;
                    case OR, XOR, ELITE_SET, SYNONYM, MAX -> Branch.Kind.OR_LIKE;
                    case AND_NOT -> Branch.Kind.AND_NOT;
                    case AND_MAYBE -> Branch.Kind.AND_MAYBE;
                    default -> null;
                }
            );
        }

        private static boolean absorbed(final List<Query> items) {
            return items.size() == 1 && items.get(0).empty();
        }
    }
}
