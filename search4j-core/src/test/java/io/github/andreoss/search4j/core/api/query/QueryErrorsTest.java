/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the query trees.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.UnimplementedError;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of the errors {@link Query} construction and accessors raise.
 *
 * @since 0.1.0
 */
final class QueryErrorsTest {

    /**
     * A term.
     */
    private static final Query FOO = Query.term("foo");

    /**
     * Another term.
     */
    private static final Query BAR = Query.term("bar");

    /**
     * Message of an assertion on an error message.
     */
    private static final String MESSAGE = "Message";

    @Test
    void rejectsNegativeFactor() {
        Assertions.assertEquals(
            "SCALE_WEIGHT requires factor >= 0",
            Assertions.assertThrows(
                InvalidArgumentError.class,
                () -> Query.scale(-1, QueryErrorsTest.FOO),
                "Negative factor"
            ).getMessage(),
            QueryErrorsTest.MESSAGE
        );
    }

    @Test
    void rejectsNegativeFactorUnderOperator() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> Query.scale(QueryOp.SCALE_WEIGHT, QueryErrorsTest.FOO, -0.5),
            "Negative factor with the operator form"
        );
    }

    @Test
    void acceptsZeroFactor() {
        Assertions.assertEquals(
            "Query(0 * foo)", Query.scale(0.0, QueryErrorsTest.FOO).description(),
            "Zero is not negative"
        );
    }

    @Test
    void rejectsWrongScaleOperator() {
        Assertions.assertEquals(
            "op must be SCALE_WEIGHT",
            Assertions.assertThrows(
                InvalidArgumentError.class,
                () -> Query.scale(QueryOp.AND, QueryErrorsTest.FOO, 1),
                "Only SCALE_WEIGHT scales"
            ).getMessage(),
            QueryErrorsTest.MESSAGE
        );
    }

    @Test
    void rejectsRangeAsBound() {
        Assertions.assertEquals(
            "op must be VALUE_LE or VALUE_GE",
            Assertions.assertThrows(
                InvalidArgumentError.class,
                () -> Query.value(QueryOp.VALUE_RANGE, new ValueNo(1L), "x"),
                "A range needs two bounds"
            ).getMessage(),
            QueryErrorsTest.MESSAGE
        );
    }

    @Test
    void rejectsOtherOperatorAsBound() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> Query.value(QueryOp.AND, new ValueNo(1L), "x"),
            "Not a value operator"
        );
    }

    @Test
    void rejectsParameterWithoutWindow() {
        Assertions.assertEquals(
            "parameter only valid with NEAR, PHRASE or ELITE_SET",
            Assertions.assertThrows(
                InvalidArgumentError.class,
                () -> Query.of(
                    QueryOp.AND, List.of(QueryErrorsTest.FOO, QueryErrorsTest.BAR), 3
                ),
                "AND takes no window"
            ).getMessage(),
            QueryErrorsTest.MESSAGE
        );
    }

    @Test
    void rejectsParameterOnIterator() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> Query.of(
                QueryOp.OR, List.of(QueryErrorsTest.FOO).iterator(), 1
            ),
            "OR takes no window"
        );
    }

    @Test
    void rejectsParameterOnTerms() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> Query.terms(QueryOp.XOR, List.of("a"), 2),
            "XOR takes no window"
        );
    }

    @Test
    void rejectsValueOperatorOverSubqueries() {
        Assertions.assertEquals(
            "op not valid with a list of subqueries",
            Assertions.assertThrows(
                InvalidArgumentError.class,
                () -> Query.of(QueryOp.VALUE_RANGE, List.of(QueryErrorsTest.FOO)),
                "A range takes no subqueries"
            ).getMessage(),
            QueryErrorsTest.MESSAGE
        );
    }

    @Test
    void rejectsInvalidOverSubqueries() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> Query.of(QueryOp.INVALID, List.of(QueryErrorsTest.FOO)),
            "The invalid query takes no subqueries"
        );
    }

    @Test
    void rejectsLeafTypeAsOperator() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> Query.of(QueryOp.LEAF_TERM, QueryErrorsTest.FOO, QueryErrorsTest.BAR),
            "A leaf type is no operator"
        );
    }

    @Test
    void rejectsScaleAsPairOperator() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> Query.of(QueryOp.SCALE_WEIGHT, QueryErrorsTest.FOO, QueryErrorsTest.BAR),
            "Scaling takes one subquery and a factor"
        );
    }

    @Test
    void rejectsWildcardAsTermOperator() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> Query.terms(QueryOp.WILDCARD, "a", "b"),
            "A wildcard takes a pattern"
        );
    }

    @Test
    void rejectsIntersectionUnderNear() {
        Assertions.assertEquals(
            "NEAR and PHRASE only currently support leaf subqueries",
            Assertions.assertThrows(
                UnimplementedError.class,
                () -> Query.of(
                    QueryOp.NEAR, QueryErrorsTest.FOO.and(QueryErrorsTest.BAR),
                    Query.term("c")
                ),
                "Reference possubqueries1"
            ).getMessage(),
            QueryErrorsTest.MESSAGE
        );
    }

    @Test
    void rejectsNearUnderPhrase() {
        Assertions.assertThrows(
            UnimplementedError.class,
            () -> Query.of(
                QueryOp.PHRASE,
                Query.of(QueryOp.NEAR, QueryErrorsTest.FOO, QueryErrorsTest.BAR),
                Query.term("c")
            ),
            "Reference possubqueries1"
        );
    }

    @Test
    void rejectsPhraseUnderNearInList() {
        Assertions.assertThrows(
            UnimplementedError.class,
            () -> Query.of(
                QueryOp.NEAR,
                List.of(
                    Query.term("c"),
                    Query.of(QueryOp.PHRASE, QueryErrorsTest.FOO, QueryErrorsTest.BAR)
                ),
                4
            ),
            "Reference possubqueries1"
        );
    }

    @Test
    void rejectsScaledTermUnderPhrase() {
        Assertions.assertThrows(
            UnimplementedError.class,
            () -> Query.of(
                QueryOp.PHRASE, Query.scale(2, QueryErrorsTest.FOO), QueryErrorsTest.BAR
            ),
            "A scaled term has no positions of its own"
        );
    }

    @Test
    void rejectsWildcardUnderNear() {
        Assertions.assertThrows(
            UnimplementedError.class,
            () -> Query.of(QueryOp.NEAR, Query.wildcard("fo"), QueryErrorsTest.BAR),
            "Expansions are not positional leaves"
        );
    }

    @Test
    void matchNothingHasNoSubquery() {
        Assertions.assertThrows(
            InvalidArgumentError.class, () -> Query.MATCH_NOTHING.subquery(0),
            "No tree at all"
        );
    }

    @Test
    void leafHasNoSubquery() {
        Assertions.assertEquals(
            "subquery() not meaningful for this query",
            Assertions.assertThrows(
                InvalidArgumentError.class, () -> QueryErrorsTest.FOO.subquery(0),
                "A term has no subqueries"
            ).getMessage(),
            QueryErrorsTest.MESSAGE
        );
    }

    @Test
    void branchHasNoFrequency() {
        Assertions.assertEquals(
            "wqf() not meaningful for this query",
            Assertions.assertThrows(
                InvalidArgumentError.class,
                () -> QueryErrorsTest.FOO.and(QueryErrorsTest.BAR).wqf(),
                "An operator has no frequency"
            ).getMessage(),
            QueryErrorsTest.MESSAGE
        );
    }

    @Test
    void branchHasNoPosition() {
        Assertions.assertEquals(
            "pos() not meaningful for this query",
            Assertions.assertThrows(
                InvalidArgumentError.class,
                () -> QueryErrorsTest.FOO.or(QueryErrorsTest.BAR).pos(),
                "An operator has no position"
            ).getMessage(),
            QueryErrorsTest.MESSAGE
        );
    }

    @Test
    void matchNothingHasNoFrequency() {
        Assertions.assertEquals(
            "Not meaningful for a match-nothing query",
            Assertions.assertThrows(
                InvalidArgumentError.class, () -> Query.MATCH_NOTHING.wqf(),
                "No tree at all"
            ).getMessage(),
            QueryErrorsTest.MESSAGE
        );
    }

    @Test
    void matchNothingHasNoPosition() {
        Assertions.assertThrows(
            InvalidArgumentError.class, () -> Query.MATCH_NOTHING.pos(),
            "No tree at all"
        );
    }

    @Test
    void expansionsHaveNoFrequency() {
        Assertions.assertThrows(
            InvalidArgumentError.class, () -> Query.wildcard("fo").wqf(),
            "A wildcard has no frequency"
        );
    }

    @Test
    void scaledQueryHasNoPosition() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> Query.scale(2, QueryErrorsTest.FOO).pos(),
            "Scaling hides the leaf"
        );
    }

    @Test
    void rejectsNullTerm() {
        Assertions.assertThrows(
            NullPointerException.class, () -> Query.term(null),
            "A term is required"
        );
    }

    @Test
    void rejectsNullSubquery() {
        Assertions.assertThrows(
            NullPointerException.class,
            () -> Query.scale(2, null),
            "A subquery is required"
        );
    }
}
