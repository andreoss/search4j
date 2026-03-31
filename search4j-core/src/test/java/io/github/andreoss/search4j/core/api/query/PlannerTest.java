/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the query trees.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The flattening step: nested operators merged into the contexts the
 * reference gathers when it turns a query into posting lists.
 *
 * @since 0.1.0
 */
final class PlannerTest {

    /**
     * Term a.
     */
    private static final Query A = Query.term("a");

    /**
     * Term b.
     */
    private static final Query B = Query.term("b");

    /**
     * Term c.
     */
    private static final Query C = Query.term("c");

    /**
     * Term d.
     */
    private static final Query D = Query.term("d");

    @ParameterizedTest(name = "{1}")
    @MethodSource({"conjunctions", "differences", "unions", "positional", "leaves"})
    void flattensAsTheReference(final Query query, final String expected,
        final double factor) {
        Assertions.assertEquals(
            expected, Planner.plan(query, factor).describe(), query.description()
        );
    }

    @Test
    void keepsTheConstructedTreeUnflattened() {
        Assertions.assertEquals(
            "Query(((a AND b) AND c))",
            PlannerTest.A.and(PlannerTest.B).and(PlannerTest.C).description(),
            "Reference dontflattensubqueries1: construction keeps the nesting"
        );
    }

    @Test
    void needsPositionsBelowAPhrase() {
        Assertions.assertEquals(
            "(AND(POS(AND(a:0, <alldocuments>:0)), c:0) PHRASE 0-2 window 2)",
            Planner.plan(
                Query.deserialise(HexFormat.of().parseHex("fa028251610f5163")),
                0.0
            ).describe(),
            "Below a phrase every document is a posting list"
        );
    }

    private static Stream<Arguments> conjunctions() {
        final Query alpha = PlannerTest.A;
        final Query beta = PlannerTest.B;
        final Query gamma = PlannerTest.C;
        final Query delta = PlannerTest.D;
        return Stream.of(
            Arguments.of(alpha.and(beta), "AND(a:1, b:1)", 1.0),
            Arguments.of(alpha.and(beta).and(gamma), "AND(a:1, b:1, c:1)", 1.0),
            Arguments.of(
                alpha.and(beta.and(gamma.and(delta))), "AND(a:1, b:1, c:1, d:1)", 1.0
            ),
            Arguments.of(alpha.and(beta.or(gamma)), "AND(a:1, OR(b:1, c:1))", 1.0),
            Arguments.of(Query.of(QueryOp.FILTER, alpha, beta), "AND(a:1, b:0)", 1.0),
            Arguments.of(
                Query.of(QueryOp.FILTER, alpha.and(beta), gamma.and(delta)),
                "AND(a:1, b:1, c:0, d:0)", 1.0
            ),
            Arguments.of(
                Query.scale(2.0, alpha.and(beta)).and(gamma), "AND(a:2, b:2, c:1)", 1.0
            ),
            Arguments.of(
                Query.of(QueryOp.FILTER, alpha, Query.MATCH_ALL), "a:1", 1.0
            ),
            Arguments.of(
                Query.MATCH_ALL.and(Query.MATCH_ALL), "<alldocuments>:0", 0.0
            ),
            Arguments.of(
                Query.MATCH_ALL.and(alpha), "AND(<alldocuments>:1, a:1)", 1.0
            ),
            Arguments.of(Query.MATCH_ALL.and(alpha), "a:0", 0.0),
            Arguments.of(alpha.and(beta), "AND(a:0.5, b:0.5)", 0.5)
        );
    }

    private static Stream<Arguments> differences() {
        final Query alpha = PlannerTest.A;
        final Query beta = PlannerTest.B;
        final Query gamma = PlannerTest.C;
        return Stream.of(
            Arguments.of(alpha.andNot(beta).andNot(gamma), "(a:1 NOT BOOL_OR(b:0, c:0))", 1.0),
            Arguments.of(alpha.andNot(beta.or(gamma)), "(a:1 NOT BOOL_OR(b:0, c:0))", 1.0),
            Arguments.of(
                alpha.andNot(beta.and(gamma)), "(a:1 NOT AND(b:0, c:0))", 1.0
            ),
            Arguments.of(alpha.andNot(beta).and(gamma), "(AND(a:1, c:1) NOT b:0)", 1.0),
            Arguments.of(
                Query.of(QueryOp.AND_MAYBE, alpha, beta.or(gamma)),
                "(a:1 MAYBE OR(b:1, c:1))", 1.0
            ),
            Arguments.of(
                Query.of(
                    QueryOp.AND_MAYBE, Query.of(QueryOp.AND_MAYBE, alpha, beta), gamma
                ),
                "(a:1 MAYBE OR(b:1, c:1))", 1.0
            ),
            Arguments.of(Query.of(QueryOp.AND_MAYBE, alpha, beta), "a:0", 0.0),
            Arguments.of(
                alpha.and(Query.of(QueryOp.AND_MAYBE, beta, gamma)),
                "(AND(a:1, b:1) MAYBE c:1)", 1.0
            ),
            Arguments.of(
                Query.of(QueryOp.AND_MAYBE, alpha.andNot(beta), gamma),
                "(a:1 NOT b:0 MAYBE c:1)", 1.0
            ),
            Arguments.of(beta.not(), "(<alldocuments>:0 NOT b:0)", 0.0),
            Arguments.of(beta.not(), "(<alldocuments>:1 NOT b:0)", 1.0),
            Arguments.of(
                Query.of(QueryOp.AND_MAYBE, alpha, Query.scale(3.0, beta)),
                "(a:1 MAYBE b:3)", 1.0
            )
        );
    }

    private static Stream<Arguments> unions() {
        final Query alpha = PlannerTest.A;
        final Query beta = PlannerTest.B;
        final Query gamma = PlannerTest.C;
        return Stream.of(
            Arguments.of(alpha.or(beta).or(gamma), "OR(a:1, b:1, c:1)", 1.0),
            Arguments.of(alpha.or(beta).or(gamma), "BOOL_OR(a:0, b:0, c:0)", 0.0),
            Arguments.of(
                Query.scale(2.0, alpha.or(beta)).or(gamma), "OR(OR(a:2, b:2), c:1)", 1.0
            ),
            Arguments.of(alpha.or(beta.and(gamma)), "OR(a:1, AND(b:1, c:1))", 1.0),
            Arguments.of(alpha.andNot(beta).or(gamma), "OR((a:1 NOT b:0), c:1)", 1.0),
            Arguments.of(alpha.xor(beta.xor(gamma)), "XOR(a:1, b:1, c:1)", 1.0),
            Arguments.of(alpha.xor(beta.and(gamma)), "XOR(a:1, AND(b:1, c:1))", 1.0),
            Arguments.of(
                Query.of(QueryOp.ELITE_SET, List.of(alpha, beta, gamma), 2),
                "(OR(a:1, b:1, c:1) ELITE 2 of 0-3)", 1.0
            ),
            Arguments.of(
                Query.of(QueryOp.ELITE_SET, List.of(alpha, beta, gamma)),
                "OR(a:1, b:1, c:1)", 1.0
            ),
            Arguments.of(
                Query.of(QueryOp.ELITE_SET, List.of(alpha.or(beta), gamma), 1),
                "(OR(a:1, b:1, c:1) ELITE 1 of 0-3)", 1.0
            ),
            Arguments.of(
                alpha.or(Query.of(QueryOp.ELITE_SET, List.of(beta, gamma), 1)),
                "(OR(a:1, b:1, c:1) ELITE 1 of 1-3)", 1.0
            ),
            Arguments.of(
                Query.of(QueryOp.SYNONYM, alpha, beta.or(gamma)),
                "SYNONYM:1(BOOL_OR(a:0, b:0, c:0))", 1.0
            ),
            Arguments.of(
                Query.of(QueryOp.SYNONYM, alpha, beta.or(gamma)),
                "BOOL_OR(a:0, b:0, c:0)", 0.0
            ),
            Arguments.of(
                Query.of(QueryOp.MAX, alpha, beta.or(gamma)), "MAX(a:1, b:1, c:1)", 1.0
            ),
            Arguments.of(
                Query.of(QueryOp.MAX, alpha, beta.or(gamma)), "BOOL_OR(a:0, b:0, c:0)", 0.0
            ),
            Arguments.of(
                alpha.and(Query.of(QueryOp.SYNONYM, beta, gamma)),
                "AND(a:1, SYNONYM:1(BOOL_OR(b:0, c:0)))", 1.0
            )
        );
    }

    private static Stream<Arguments> positional() {
        final Query alpha = PlannerTest.A;
        final Query beta = PlannerTest.B;
        final Query gamma = PlannerTest.C;
        return Stream.of(
            Arguments.of(
                Query.of(QueryOp.PHRASE, alpha, beta).and(gamma),
                "(AND(a:1, b:1, c:1) PHRASE 0-2 window 2)", 1.0
            ),
            Arguments.of(
                Query.of(QueryOp.PHRASE, alpha, beta.or(gamma)),
                "(AND(a:1, POS(OR(b:1, c:1))) PHRASE 0-2 window 2)", 1.0
            ),
            Arguments.of(
                Query.of(QueryOp.NEAR, alpha, beta.or(gamma)),
                "(AND(a:0, POS(BOOL_OR(b:0, c:0))) NEAR 0-2 window 2)", 0.0
            ),
            Arguments.of(
                Query.of(QueryOp.NEAR, List.of(alpha, beta), 5)
                    .and(Query.of(QueryOp.PHRASE, gamma, PlannerTest.D)),
                "(AND(a:1, b:1, c:1, d:1) NEAR 0-2 window 5 PHRASE 2-4 window 2)", 1.0
            ),
            Arguments.of(
                Query.of(QueryOp.PHRASE, alpha, beta).andNot(gamma),
                "(AND(a:1, b:1) NOT c:0 PHRASE 0-2 window 2)", 1.0
            )
        );
    }

    private static Stream<Arguments> leaves() {
        return Stream.of(
            Arguments.of(Query.MATCH_NOTHING, "NOTHING", 1.0),
            Arguments.of(Query.MATCH_ALL, "<alldocuments>:1", 1.0),
            Arguments.of(Query.invalid(), "<INVALID>:1", 1.0),
            Arguments.of(
                Query.valueRange(new ValueNo(1L), "a", "b"), "VALUE_RANGE 1 a b:1", 1.0
            ),
            Arguments.of(Query.wildcard("pre"), "WILDCARD SYNONYM pre:1", 1.0),
            Arguments.of(Query.editDistance("e"), "EDIT_DISTANCE SYNONYM e~2:1", 1.0),
            Arguments.of(
                Query.scale(0.5, Query.scale(4.0, PlannerTest.A)), "a:2", 1.0
            ),
            Arguments.of(
                Query.value(QueryOp.VALUE_GE, new ValueNo(2L), "m").and(PlannerTest.A),
                "AND(VALUE_GE 2 m:1, a:1)", 1.0
            )
        );
    }
}
