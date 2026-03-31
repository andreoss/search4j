/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the query trees.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests of {@link Query#description()} against the texts the reference
 * prints for the same trees.
 *
 * @since 0.1.0
 */
final class QueryDescriptionTest {

    /**
     * Description of a match-nothing query.
     */
    private static final String NOTHING = "Query()";

    /**
     * Description of a match-all query.
     */
    private static final String ALL = "Query(<alldocuments>)";

    /**
     * A term.
     */
    private static final Query FOO = Query.term("foo");

    /**
     * Another term.
     */
    private static final Query BAR = Query.term("bar");

    /**
     * A third term.
     */
    private static final Query BAZ = Query.term("baz");

    /**
     * A short term.
     */
    private static final Query TA = Query.term("a");

    /**
     * Another short term.
     */
    private static final Query TB = Query.term("b");

    /**
     * A third short term.
     */
    private static final Query TC = Query.term("c");

    /**
     * Every operator taking subqueries.
     */
    private static final List<QueryOp> BRANCHES = List.of(
        QueryOp.AND, QueryOp.OR, QueryOp.AND_NOT, QueryOp.XOR,
        QueryOp.AND_MAYBE, QueryOp.FILTER, QueryOp.NEAR, QueryOp.PHRASE,
        QueryOp.ELITE_SET, QueryOp.SYNONYM, QueryOp.MAX
    );

    @ParameterizedTest
    @MethodSource({"reference", "overloads", "operators", "absorption", "collapses"})
    void describesAsTheReference(final Query query, final String expected) {
        Assertions.assertEquals(
            expected, query.description(), "Description text"
        );
    }

    @Test
    void singleSubqueryCollapses() {
        Assertions.assertEquals(
            Collections.nCopies(QueryDescriptionTest.BRANCHES.size(), "Query(test)"),
            QueryDescriptionTest.BRANCHES.stream()
                .map(type -> Query.of(type, List.of(Query.term("test"))))
                .map(Query::description)
                .toList(),
            "Reference singlesubquery1"
        );
    }

    @Test
    void singleMatchNothingCollapses() {
        Assertions.assertEquals(
            Collections.nCopies(
                QueryDescriptionTest.BRANCHES.size(), QueryDescriptionTest.NOTHING
            ),
            QueryDescriptionTest.BRANCHES.stream()
                .map(type -> Query.of(type, List.of(Query.MATCH_NOTHING)))
                .map(Query::description)
                .toList(),
            "Reference singlesubquery2"
        );
    }

    @Test
    void singleMatchAllCollapses() {
        final List<QueryOp> types = QueryDescriptionTest.BRANCHES.stream()
            .filter(type -> type != QueryOp.NEAR && type != QueryOp.PHRASE)
            .toList();
        Assertions.assertEquals(
            Collections.nCopies(types.size(), QueryDescriptionTest.ALL),
            types.stream()
                .map(type -> Query.of(type, List.of(Query.MATCH_ALL)))
                .map(Query::description)
                .toList(),
            "Reference singlesubquery3"
        );
    }

    @Test
    void noSubqueriesMatchNothing() {
        Assertions.assertEquals(
            Collections.nCopies(QueryDescriptionTest.BRANCHES.size(), true),
            QueryDescriptionTest.BRANCHES.stream()
                .map(type -> Query.of(type, List.of()).empty())
                .toList(),
            "Reference emptyquery4"
        );
    }

    @Test
    void termPairsEqualQueryPairs() {
        Assertions.assertEquals(
            QueryDescriptionTest.BRANCHES.stream()
                .map(type -> Query.terms(type, "a", "b").description())
                .toList(),
            QueryDescriptionTest.BRANCHES.stream()
                .map(type -> Query.of(type, QueryDescriptionTest.TA, QueryDescriptionTest.TB))
                .map(Query::description)
                .toList(),
            "Reference pairwisequery1"
        );
    }

    @Test
    void emptyPairsOfEveryOperator() {
        final List<QueryOp> types = List.of(
            QueryOp.AND, QueryOp.OR, QueryOp.XOR, QueryOp.AND_MAYBE,
            QueryOp.AND_NOT
        );
        final Query test = Query.term("test");
        Assertions.assertEquals(
            List.of(
                QueryDescriptionTest.NOTHING, QueryDescriptionTest.NOTHING,
                QueryDescriptionTest.NOTHING, "Query(test)", "Query(test)",
                QueryDescriptionTest.NOTHING, "Query(test)", "Query(test)",
                QueryDescriptionTest.NOTHING, QueryDescriptionTest.NOTHING,
                "Query(test)", QueryDescriptionTest.NOTHING,
                QueryDescriptionTest.NOTHING, "Query(test)",
                QueryDescriptionTest.NOTHING
            ),
            types.stream()
                .flatMap(type -> QueryDescriptionTest.pairs(type, test))
                .map(Query::description)
                .toList(),
            "Reference emptyquery3"
        );
    }

    static Stream<Arguments> reference() {
        final Query same = QueryDescriptionTest.FOO.and(QueryDescriptionTest.BAR);
        final Query exclusive = QueryDescriptionTest.FOO.xor(QueryDescriptionTest.BAR);
        return Stream.of(
            Arguments.of(Query.MATCH_ALL, QueryDescriptionTest.ALL),
            Arguments.of(Query.MATCH_NOTHING, QueryDescriptionTest.NOTHING),
            Arguments.of(
                Query.of(
                    QueryOp.AND,
                    List.of(QueryDescriptionTest.FOO, Query.MATCH_NOTHING)
                ),
                QueryDescriptionTest.NOTHING
            ),
            Arguments.of(
                Query.of(QueryOp.AND, QueryDescriptionTest.FOO, Query.MATCH_NOTHING),
                QueryDescriptionTest.NOTHING
            ),
            Arguments.of(
                Query.of(QueryOp.AND, Query.MATCH_NOTHING, QueryDescriptionTest.FOO),
                QueryDescriptionTest.NOTHING
            ),
            Arguments.of(
                Query.of(
                    QueryOp.AND_MAYBE, QueryDescriptionTest.FOO, Query.MATCH_NOTHING
                ),
                "Query(foo)"
            ),
            Arguments.of(
                Query.of(
                    QueryOp.AND_MAYBE, Query.MATCH_NOTHING, QueryDescriptionTest.FOO
                ),
                QueryDescriptionTest.NOTHING
            ),
            Arguments.of(
                Query.of(
                    QueryOp.AND_NOT, QueryDescriptionTest.FOO, Query.MATCH_NOTHING
                ),
                "Query(foo)"
            ),
            Arguments.of(
                Query.of(
                    QueryOp.AND_NOT, Query.MATCH_NOTHING, QueryDescriptionTest.FOO
                ),
                QueryDescriptionTest.NOTHING
            ),
            Arguments.of(same, "Query((foo AND bar))"),
            Arguments.of(
                same.and(QueryDescriptionTest.BAZ), "Query(((foo AND bar) AND baz))"
            ),
            Arguments.of(
                same.and(same), "Query(((foo AND bar) AND (foo AND bar)))"
            ),
            Arguments.of(same.and(Query.MATCH_NOTHING), QueryDescriptionTest.NOTHING),
            Arguments.of(
                QueryDescriptionTest.FOO.or(QueryDescriptionTest.BAR)
                    .and(QueryDescriptionTest.BAZ),
                "Query(((foo OR bar) AND baz))"
            ),
            Arguments.of(
                QueryDescriptionTest.FOO.or(QueryDescriptionTest.BAR)
                    .or(QueryDescriptionTest.BAZ),
                "Query(((foo OR bar) OR baz))"
            ),
            Arguments.of(
                QueryDescriptionTest.FOO.or(QueryDescriptionTest.BAR)
                    .or(Query.MATCH_NOTHING),
                "Query((foo OR bar))"
            ),
            Arguments.of(
                same.or(QueryDescriptionTest.BAZ), "Query(((foo AND bar) OR baz))"
            ),
            Arguments.of(
                exclusive.xor(QueryDescriptionTest.BAZ),
                "Query(((foo XOR bar) XOR baz))"
            ),
            Arguments.of(
                exclusive.xor(exclusive),
                "Query(((foo XOR bar) XOR (foo XOR bar)))"
            ),
            Arguments.of(exclusive.xor(Query.MATCH_NOTHING), "Query((foo XOR bar))"),
            Arguments.of(
                same.xor(QueryDescriptionTest.BAZ), "Query(((foo AND bar) XOR baz))"
            )
        );
    }

    static Stream<Arguments> overloads() {
        final Query exclusive = QueryDescriptionTest.FOO.xor(QueryDescriptionTest.BAR);
        final Query either = QueryDescriptionTest.TA.or(QueryDescriptionTest.TB);
        final Query one = Query.term("one");
        final Query two = Query.term("two");
        return Stream.of(
            Arguments.of(
                QueryDescriptionTest.FOO.andNot(QueryDescriptionTest.BAR),
                "Query((foo AND_NOT bar))"
            ),
            Arguments.of(
                QueryDescriptionTest.BAR.not(),
                "Query((<alldocuments> AND_NOT bar))"
            ),
            Arguments.of(
                QueryDescriptionTest.FOO.and(Query.MATCH_NOTHING),
                QueryDescriptionTest.NOTHING
            ),
            Arguments.of(
                QueryDescriptionTest.FOO.or(QueryDescriptionTest.BAR),
                "Query((foo OR bar))"
            ),
            Arguments.of(QueryDescriptionTest.FOO.or(Query.MATCH_NOTHING), "Query(foo)"),
            Arguments.of(exclusive, "Query((foo XOR bar))"),
            Arguments.of(
                QueryDescriptionTest.FOO.xor(Query.MATCH_NOTHING), "Query(foo)"
            ),
            Arguments.of(Query.scale(1.25, one.or(two)), "Query(1.25 * (one OR two))"),
            Arguments.of(Query.scale(42, one.and(two)), "Query(42 * (one AND two))"),
            Arguments.of(Query.scale(1.0 / 2.0, one), "Query(0.5 * one)"),
            Arguments.of(
                Query.of(QueryOp.NEAR, either, either),
                "Query(((a OR b) NEAR 2 (a OR b)))"
            ),
            Arguments.of(
                Query.of(QueryOp.PHRASE, either, either),
                "Query(((a OR b) PHRASE 2 (a OR b)))"
            ),
            Arguments.of(
                Query.term(new String(new char[] {0, 0x1F})), "Query(\\x00\\x1f)"
            ),
            Arguments.of(Query.term("back\\slash"), "Query(back\\x5cslash)"),
            Arguments.of(
                Query.term(String.format("D%c_%c~", (char) 0x7F, (char) 0x7F)),
                "Query(D\\x7f_\\x7f~)"
            ),
            Arguments.of(
                Query.of(
                    QueryOp.OR,
                    List.of(
                        Query.term("beer", new TermCount(1), new TermPos(1)),
                        Query.term("beer", new TermCount(1), new TermPos(1))
                    )
                ),
                "Query((beer@1 OR beer@1))"
            ),
            Arguments.of(
                Query.of(
                    QueryOp.OR,
                    List.of(
                        Query.term("wibble"), Query.term("wobble"),
                        Query.terms(QueryOp.OR, "jelly", "belly")
                    )
                ),
                "Query((wibble OR wobble OR (jelly OR belly)))"
            ),
            Arguments.of(
                Query.of(
                    QueryOp.AND,
                    List.of(
                        Query.terms(QueryOp.AND, "jelly", "belly"),
                        Query.term("wibble"), Query.term("wobble")
                    )
                ),
                "Query(((jelly AND belly) AND wibble AND wobble))"
            ),
            Arguments.of(
                Query.scale(QueryOp.SCALE_WEIGHT, Query.MATCH_NOTHING, 3.0),
                QueryDescriptionTest.NOTHING
            ),
            Arguments.of(
                Query.value(QueryOp.VALUE_GE, new ValueNo(0L), ""),
                QueryDescriptionTest.ALL
            ),
            Arguments.of(Query.term("hello"), "Query(hello)"),
            Arguments.of(
                Query.term("hello", new TermCount(1), new TermPos(1)),
                "Query(hello@1)"
            ),
            Arguments.of(
                Query.of(QueryOp.OR, Query.term("hello"), Query.term("world")),
                "Query((hello OR world))"
            ),
            Arguments.of(
                Query.of(
                    QueryOp.OR,
                    Query.term("hello", new TermCount(1), new TermPos(1)),
                    Query.term("world", new TermCount(1), new TermPos(1))
                ),
                "Query((hello@1 OR world@1))"
            )
        );
    }

    static Stream<Arguments> operators() {
        final List<Query> three = List.of(
            QueryDescriptionTest.TA, QueryDescriptionTest.TB, QueryDescriptionTest.TC
        );
        return Stream.of(
            Arguments.of(
                Query.of(QueryOp.AND_NOT, QueryDescriptionTest.TA, QueryDescriptionTest.TB),
                "Query((a AND_NOT b))"
            ),
            Arguments.of(
                Query.of(QueryOp.AND_MAYBE, QueryDescriptionTest.TA, QueryDescriptionTest.TB),
                "Query((a AND_MAYBE b))"
            ),
            Arguments.of(
                Query.of(QueryOp.FILTER, QueryDescriptionTest.TA, QueryDescriptionTest.TB),
                "Query((a FILTER b))"
            ),
            Arguments.of(
                Query.of(QueryOp.NEAR, QueryDescriptionTest.TA, QueryDescriptionTest.TB),
                "Query((a NEAR 2 b))"
            ),
            Arguments.of(
                Query.of(QueryOp.PHRASE, QueryDescriptionTest.TA, QueryDescriptionTest.TB),
                "Query((a PHRASE 2 b))"
            ),
            Arguments.of(
                Query.of(QueryOp.ELITE_SET, QueryDescriptionTest.TA, QueryDescriptionTest.TB),
                "Query((a ELITE_SET 10 b))"
            ),
            Arguments.of(
                Query.of(QueryOp.SYNONYM, QueryDescriptionTest.TA, QueryDescriptionTest.TB),
                "Query((a SYNONYM b))"
            ),
            Arguments.of(
                Query.of(QueryOp.MAX, QueryDescriptionTest.TA, QueryDescriptionTest.TB),
                "Query((a MAX b))"
            ),
            Arguments.of(
                Query.of(QueryOp.XOR, QueryDescriptionTest.TA, QueryDescriptionTest.TB),
                "Query((a XOR b))"
            ),
            Arguments.of(
                Query.of(QueryOp.NEAR, three, 8), "Query((a NEAR 8 b NEAR 8 c))"
            ),
            Arguments.of(
                Query.of(QueryOp.PHRASE, three, 8), "Query((a PHRASE 8 b PHRASE 8 c))"
            ),
            Arguments.of(
                Query.of(QueryOp.PHRASE, three), "Query((a PHRASE 3 b PHRASE 3 c))"
            ),
            Arguments.of(
                Query.of(QueryOp.NEAR, three.iterator(), 0),
                "Query((a NEAR 3 b NEAR 3 c))"
            ),
            Arguments.of(
                Query.of(QueryOp.ELITE_SET, three, 7),
                "Query((a ELITE_SET 7 b ELITE_SET 7 c))"
            ),
            Arguments.of(
                Query.of(QueryOp.ELITE_SET, three),
                "Query((a ELITE_SET 10 b ELITE_SET 10 c))"
            ),
            Arguments.of(
                Query.of(QueryOp.FILTER, three), "Query((a FILTER b FILTER c))"
            ),
            Arguments.of(Query.of(QueryOp.MAX, three), "Query((a MAX b MAX c))"),
            Arguments.of(
                Query.of(QueryOp.AND_NOT, three), "Query((a AND_NOT b AND_NOT c))"
            ),
            Arguments.of(
                Query.of(QueryOp.AND_MAYBE, three),
                "Query((a AND_MAYBE b AND_MAYBE c))"
            ),
            Arguments.of(
                Query.of(QueryOp.SYNONYM, three), "Query((a SYNONYM b SYNONYM c))"
            ),
            Arguments.of(
                Query.terms(
                    QueryOp.XOR,
                    List.of("hack", "which", "paragraph", "is", "return")
                ),
                "Query((hack XOR which XOR paragraph XOR is XOR return))"
            ),
            Arguments.of(
                Query.terms(QueryOp.PHRASE, List.of("expect", "to", "mset"), 5),
                "Query((expect PHRASE 5 to PHRASE 5 mset))"
            ),
            Arguments.of(
                Query.terms(QueryOp.NEAR, "", "a"), "Query((<alldocuments> NEAR 2 a))"
            ),
            Arguments.of(
                Query.of(
                    QueryOp.PHRASE,
                    Query.term("is").or(Query.term("as")).or(Query.term("be")),
                    QueryDescriptionTest.TA
                ),
                "Query((((is OR as) OR be) PHRASE 2 a))"
            ),
            Arguments.of(
                Query.of(
                    QueryOp.NEAR, QueryDescriptionTest.TA,
                    Query.term("is").or(Query.term("as"))
                ),
                "Query((a NEAR 2 (is OR as)))"
            ),
            Arguments.of(
                Query.of(QueryOp.NEAR, Query.term("and"), Query.MATCH_ALL),
                QueryDescriptionTest.NOTHING
            ),
            Arguments.of(
                Query.of(QueryOp.PHRASE, Query.term("at"), Query.MATCH_NOTHING),
                QueryDescriptionTest.NOTHING
            ),
            Arguments.of(
                Query.of(QueryOp.PHRASE, Query.MATCH_ALL, Query.term("at")),
                QueryDescriptionTest.NOTHING
            ),
            Arguments.of(
                Query.of(QueryOp.AND, three, 0), "Query((a AND b AND c))"
            ),
            Arguments.of(
                Query.of(QueryOp.OR, three.iterator(), 0), "Query((a OR b OR c))"
            ),
            Arguments.of(
                Query.of(QueryOp.XOR, three), "Query((a XOR b XOR c))"
            )
        );
    }

    static Stream<Arguments> absorption() {
        final Query scaled = Query.scale(2, QueryDescriptionTest.TB);
        return Stream.of(
            Arguments.of(
                Query.of(QueryOp.AND_NOT, QueryDescriptionTest.TA, scaled),
                "Query((a AND_NOT b))"
            ),
            Arguments.of(
                Query.of(QueryOp.AND_NOT, scaled, QueryDescriptionTest.TA),
                "Query((2 * b AND_NOT a))"
            ),
            Arguments.of(
                Query.of(
                    QueryOp.AND_NOT,
                    List.of(
                        QueryDescriptionTest.TA, Query.MATCH_NOTHING,
                        QueryDescriptionTest.TB
                    )
                ),
                "Query((a AND_NOT b))"
            ),
            Arguments.of(
                Query.of(
                    QueryOp.AND_NOT,
                    List.of(
                        Query.MATCH_NOTHING, QueryDescriptionTest.TA,
                        QueryDescriptionTest.TB
                    )
                ),
                QueryDescriptionTest.NOTHING
            ),
            Arguments.of(
                Query.of(
                    QueryOp.AND_MAYBE,
                    List.of(
                        QueryDescriptionTest.TA, Query.MATCH_NOTHING,
                        QueryDescriptionTest.TB
                    )
                ),
                "Query((a AND_MAYBE b))"
            ),
            Arguments.of(
                Query.of(
                    QueryOp.AND_MAYBE,
                    List.of(
                        Query.MATCH_NOTHING, QueryDescriptionTest.TA,
                        QueryDescriptionTest.TB
                    )
                ),
                QueryDescriptionTest.NOTHING
            ),
            Arguments.of(
                Query.of(QueryOp.AND_MAYBE, QueryDescriptionTest.TA, scaled),
                "Query((a AND_MAYBE 2 * b))"
            ),
            Arguments.of(
                Query.of(
                    QueryOp.AND,
                    List.of(
                        QueryDescriptionTest.TA, Query.MATCH_NOTHING,
                        QueryDescriptionTest.TB
                    )
                ),
                QueryDescriptionTest.NOTHING
            ),
            Arguments.of(
                Query.of(
                    QueryOp.OR,
                    List.of(
                        Query.MATCH_NOTHING, QueryDescriptionTest.TA,
                        Query.MATCH_NOTHING, QueryDescriptionTest.TB
                    )
                ),
                "Query((a OR b))"
            ),
            Arguments.of(
                Query.of(
                    QueryOp.OR, List.of(Query.MATCH_NOTHING, Query.MATCH_NOTHING)
                ),
                QueryDescriptionTest.NOTHING
            ),
            Arguments.of(
                Query.of(
                    QueryOp.FILTER,
                    List.of(
                        QueryDescriptionTest.TA, QueryDescriptionTest.TB,
                        Query.MATCH_NOTHING
                    )
                ),
                QueryDescriptionTest.NOTHING
            )
        );
    }

    static Stream<Arguments> collapses() {
        final Query pair = QueryDescriptionTest.TA.and(QueryDescriptionTest.TB);
        return Stream.of(
            Arguments.of(
                Query.of(
                    QueryOp.SYNONYM,
                    List.of(Query.wildcard(Wildcard.of("fo").withCombiner(QueryOp.OR)))
                ),
                "Query(WILDCARD SYNONYM fo)"
            ),
            Arguments.of(
                Query.of(
                    QueryOp.SYNONYM,
                    List.of(
                        Query.editDistance(
                            EditDistance.of("possum").withCombiner(QueryOp.MAX)
                        )
                    )
                ),
                "Query(EDIT_DISTANCE SYNONYM possum~2)"
            ),
            Arguments.of(
                Query.of(QueryOp.SYNONYM, List.of(pair)),
                "Query((SYNONYM (a AND b)))"
            ),
            Arguments.of(
                Query.of(
                    QueryOp.SYNONYM,
                    List.of(
                        Query.of(
                            QueryOp.SYNONYM, QueryDescriptionTest.TA,
                            QueryDescriptionTest.TB
                        )
                    )
                ),
                "Query((a SYNONYM b))"
            ),
            Arguments.of(
                Query.of(QueryOp.SYNONYM, List.of(Query.MATCH_ALL)),
                QueryDescriptionTest.ALL
            ),
            Arguments.of(
                Query.of(QueryOp.SYNONYM, Query.MATCH_NOTHING, pair),
                "Query((SYNONYM (a AND b)))"
            ),
            Arguments.of(
                Query.of(
                    QueryOp.SYNONYM, Query.wildcard("fo"), Query.wildcard("Sfo")
                ),
                "Query((WILDCARD SYNONYM fo SYNONYM WILDCARD SYNONYM Sfo))"
            ),
            Arguments.of(
                Query.of(QueryOp.ELITE_SET, List.of(QueryDescriptionTest.TA)),
                "Query(a)"
            ),
            Arguments.of(
                Query.of(QueryOp.OR, List.of()), QueryDescriptionTest.NOTHING
            ),
            Arguments.of(
                Query.of(QueryOp.INVALID, List.of()), QueryDescriptionTest.NOTHING
            ),
            Arguments.of(
                Query.of(QueryOp.VALUE_RANGE, List.of(), 5),
                QueryDescriptionTest.NOTHING
            ),
            Arguments.of(
                Query.terms(QueryOp.NEAR, List.of()), QueryDescriptionTest.NOTHING
            ),
            Arguments.of(
                Query.scale(2.5, Query.scale(2, QueryDescriptionTest.FOO)),
                "Query(2.5 * 2 * foo)"
            ),
            Arguments.of(
                Query.scale(1, Query.MATCH_ALL), "Query(1 * <alldocuments>)"
            ),
            Arguments.of(
                Query.scale(
                    QueryOp.SCALE_WEIGHT,
                    Query.value(QueryOp.VALUE_LE, new ValueNo(3L), "x"), 2
                ),
                "Query(VALUE_LE 3 x)"
            ),
            Arguments.of(
                Query.scale(
                    QueryOp.SCALE_WEIGHT,
                    Query.value(QueryOp.VALUE_GE, new ValueNo(3L), "x"), 2
                ),
                "Query(VALUE_GE 3 x)"
            ),
            Arguments.of(
                Query.scale(
                    QueryOp.SCALE_WEIGHT,
                    Query.valueRange(new ValueNo(2L), "10", "20"), 0
                ),
                "Query(VALUE_RANGE 2 10 20)"
            ),
            Arguments.of(
                Query.scale(0, Query.valueRange(new ValueNo(2L), "10", "20")),
                "Query(0 * VALUE_RANGE 2 10 20)"
            ),
            Arguments.of(
                Query.scale(QueryOp.SCALE_WEIGHT, QueryDescriptionTest.FOO, 2),
                "Query(2 * foo)"
            )
        );
    }

    private static Stream<Query> pairs(final QueryOp type, final Query test) {
        return Stream.of(
            Query.of(type, Query.MATCH_NOTHING, test),
            Query.of(type, test, Query.MATCH_NOTHING),
            Query.of(type, Query.MATCH_NOTHING, Query.MATCH_NOTHING)
        );
    }
}
