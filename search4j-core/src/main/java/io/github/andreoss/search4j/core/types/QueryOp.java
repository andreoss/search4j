/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query operator and leaf type enumeration.
 */
package io.github.andreoss.search4j.core.types;

/**
 * Operator and leaf type of a query tree node.
 *
 * <p>Includes the leaf type values {@code LEAF_TERM} to
 * {@code LEAF_MATCH_NOTHING}.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings({"PMD.ProhibitPublicStaticMethods", "PMD.LongVariable"})
public enum QueryOp {

    /**
     * Intersection; all subqueries must match.
     */
    AND(0),

    /**
     * Union; any subquery may match.
     */
    OR(1),

    /**
     * Difference; the first subquery matches and the rest do not.
     */
    AND_NOT(2),

    /**
     * Exclusive or; an odd number of subqueries match.
     */
    XOR(3),

    /**
     * Matches what the first subquery matches; the others add weight only.
     */
    AND_MAYBE(4),

    /**
     * Intersection taking weight only from the first subquery.
     */
    FILTER(5),

    /**
     * Proximity; subqueries appear within a window, in any order.
     */
    NEAR(6),

    /**
     * Ordered proximity; subqueries appear within a window, in query order.
     */
    PHRASE(7),

    /**
     * A value slot lies within a range.
     */
    VALUE_RANGE(8),

    /**
     * Scale the weight of a single subquery.
     */
    SCALE_WEIGHT(9),

    /**
     * Union of the best N subqueries by estimated weight.
     */
    ELITE_SET(10),

    /**
     * A value slot is greater than or equal to a bound.
     */
    VALUE_GE(11),

    /**
     * A value slot is less than or equal to a bound.
     */
    VALUE_LE(12),

    /**
     * Union weighted as a single term.
     */
    SYNONYM(13),

    /**
     * Union taking the maximum subquery weight.
     */
    MAX(14),

    /**
     * Term expansion by pattern.
     */
    WILDCARD(15),

    /**
     * Term expansion within an edit distance of a target.
     */
    EDIT_DISTANCE(16),

    /**
     * Invalid query, used as a placeholder.
     */
    INVALID(99),

    /**
     * Leaf type of a term.
     */
    LEAF_TERM(100),

    /**
     * Leaf type of a posting source.
     */
    LEAF_POSTING_SOURCE(101),

    /**
     * Leaf type matching every document.
     */
    LEAF_MATCH_ALL(102),

    /**
     * Leaf type matching no document.
     */
    LEAF_MATCH_NOTHING(103);

    /**
     * The numeric value.
     */
    private final int code;

    /**
     * Creates the operator.
     *
     * @param code The numeric value
     */
    QueryOp(final int code) {
        this.code = code;
    }

    /**
     * Numeric code of the operator or leaf type.
     *
     * @return Code below 100 for operators, 100 and above for leaves
     */
    public int code() {
        return this.code;
    }

    /**
     * Tests whether this value denotes a leaf rather than an operator.
     *
     * @return True for leaf types
     */
    public boolean leaf() {
        return this.code >= 100;
    }

    /**
     * Resolves an operator from its numeric value.
     *
     * @param code Code of a constant; others raise an illegal-argument error
     * @return Operator or leaf type carrying the code
     */
    public static QueryOp of(final int code) {
        for (final QueryOp value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException(
            String.format("No query operator: %d", code)
        );
    }
}
