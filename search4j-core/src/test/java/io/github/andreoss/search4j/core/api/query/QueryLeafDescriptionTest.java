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
import io.github.andreoss.search4j.core.types.WildcardPattern;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests of {@link Query#description()} for leaves, expansions, scaled
 * queries and the trees a query parser builds, against the texts the
 * reference prints.
 *
 * @since 0.1.0
 */
final class QueryLeafDescriptionTest {

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

    @ParameterizedTest
    @MethodSource({"leaves", "expansions", "parsed", "scaled"})
    void describesAsTheReference(final Query query, final String expected) {
        Assertions.assertEquals(
            expected, query.description(), "Description text"
        );
    }

    static Stream<Arguments> leaves() {
        final ValueNo slot = new ValueNo(7L);
        return Stream.of(
            Arguments.of(
                Query.value(QueryOp.VALUE_GE, new ValueNo(1234L), "x"),
                "Query(VALUE_GE 1234 x)"
            ),
            Arguments.of(
                Query.value(QueryOp.VALUE_LE, new ValueNo(1234L), "x"),
                "Query(VALUE_LE 1234 x)"
            ),
            Arguments.of(
                Query.value(QueryOp.VALUE_LE, slot, ""), "Query(VALUE_LE 7 )"
            ),
            Arguments.of(Query.valueRange(slot, "", "z"), "Query(VALUE_LE 7 z)"),
            Arguments.of(
                Query.valueRange(slot, "b", "a"), QueryLeafDescriptionTest.NOTHING
            ),
            Arguments.of(
                Query.valueRange(slot, "a", "a"), "Query(VALUE_RANGE 7 a a)"
            ),
            Arguments.of(
                Query.valueRange(slot, "a\\b", "b\tc"),
                "Query(VALUE_RANGE 7 a\\x5cb b\\x09c)"
            ),
            Arguments.of(Query.wildcard("fo"), "Query(WILDCARD SYNONYM fo)"),
            Arguments.of(
                Query.wildcard(Wildcard.of("fo").withCombiner(QueryOp.OR)),
                "Query(WILDCARD OR fo)"
            ),
            Arguments.of(
                Query.wildcard(Wildcard.of("fo").withCombiner(QueryOp.MAX)),
                "Query(WILDCARD MAX fo)"
            ),
            Arguments.of(
                Query.wildcard(Wildcard.of("?").withGlob(WildcardPattern.GLOB)),
                "Query(WILDCARD SYNONYM ?)"
            ),
            Arguments.of(
                Query.wildcard(Wildcard.of("??").withGlob(WildcardPattern.GLOB)),
                "Query(WILDCARD SYNONYM ??)"
            ),
            Arguments.of(
                Query.wildcard(Wildcard.of("*??").withGlob(WildcardPattern.GLOB)),
                "Query(WILDCARD SYNONYM *??)"
            ),
            Arguments.of(
                Query.wildcard(Wildcard.of("*a").withGlob(WildcardPattern.GLOB)),
                "Query(WILDCARD SYNONYM *a)"
            ),
            Arguments.of(Query.wildcard("*"), "Query(WILDCARD SYNONYM *)"),
            Arguments.of(
                Query.wildcard(Wildcard.of("t?st").withGlob(WildcardPattern.SINGLE)),
                "Query(WILDCARD SYNONYM t?st)"
            ),
            Arguments.of(
                Query.wildcard(Wildcard.of("*").withGlob(WildcardPattern.SINGLE)),
                "Query(WILDCARD SYNONYM *)"
            ),
            Arguments.of(
                Query.wildcard(Wildcard.of("?").withGlob(WildcardPattern.MULTI)),
                "Query(WILDCARD SYNONYM ?)"
            ),
            Arguments.of(
                Query.wildcard(Wildcard.of("?*").withGlob(WildcardPattern.MULTI)),
                "Query(WILDCARD SYNONYM ?*)"
            ),
            Arguments.of(
                Query.wildcard(Wildcard.of("*?").withGlob(WildcardPattern.SINGLE)),
                "Query(WILDCARD SYNONYM *?)"
            )
        );
    }

    static Stream<Arguments> expansions() {
        return Stream.of(
            Arguments.of(Query.wildcard(""), QueryLeafDescriptionTest.ALL),
            Arguments.of(
                Query.wildcard(Wildcard.of("").withGlob(WildcardPattern.GLOB)),
                QueryLeafDescriptionTest.NOTHING
            ),
            Arguments.of(
                Query.wildcard(Wildcard.of("").withGlob(WildcardPattern.SINGLE)),
                QueryLeafDescriptionTest.NOTHING
            ),
            Arguments.of(
                Query.wildcard(Wildcard.of("*").withGlob(WildcardPattern.GLOB)),
                QueryLeafDescriptionTest.ALL
            ),
            Arguments.of(
                Query.wildcard(Wildcard.of("**").withGlob(WildcardPattern.GLOB)),
                QueryLeafDescriptionTest.ALL
            ),
            Arguments.of(
                Query.wildcard(Wildcard.of("?*").withGlob(WildcardPattern.GLOB)),
                QueryLeafDescriptionTest.ALL
            ),
            Arguments.of(
                Query.wildcard(Wildcard.of("*?").withGlob(WildcardPattern.GLOB)),
                QueryLeafDescriptionTest.ALL
            ),
            Arguments.of(
                Query.wildcard(Wildcard.of("*?*").withGlob(WildcardPattern.GLOB)),
                QueryLeafDescriptionTest.ALL
            ),
            Arguments.of(
                Query.wildcard(Wildcard.of("*").withGlob(WildcardPattern.MULTI)),
                QueryLeafDescriptionTest.ALL
            ),
            Arguments.of(
                Query.wildcard(Wildcard.of("b*anas").withGlob(WildcardPattern.MULTI)),
                "Query(WILDCARD SYNONYM b*anas)"
            ),
            Arguments.of(
                Query.editDistance("possum"), "Query(EDIT_DISTANCE SYNONYM possum~2)"
            ),
            Arguments.of(
                Query.editDistance(EditDistance.of("Spossum").withPrefix(1)),
                "Query(EDIT_DISTANCE SYNONYM Spossum~2 fixed_prefix_len=1)"
            ),
            Arguments.of(
                Query.editDistance(
                    EditDistance.of("museum").withEdits(3).withCombiner(QueryOp.OR)
                ),
                "Query(EDIT_DISTANCE OR museum~3)"
            ),
            Arguments.of(
                Query.editDistance(
                    EditDistance.of("museum").withEdits(0).withCombiner(QueryOp.MAX)
                ),
                "Query(EDIT_DISTANCE MAX museum~0)"
            ),
            Arguments.of(Query.invalid(), "Query(<INVALID>)"),
            Arguments.of(
                Query.term("foo", new TermCount(2), new TermPos(1)), "Query(foo#2@1)"
            ),
            Arguments.of(Query.term("foo", new TermCount(3)), "Query(foo#3)"),
            Arguments.of(
                Query.term("", new TermCount(2)), "Query(<alldocuments>#2)"
            ),
            Arguments.of(
                Query.term("", new TermCount(1), new TermPos(5)),
                "Query(<alldocuments>@5)"
            ),
            Arguments.of(
                Query.term("", TermCount.ZERO), "Query(<alldocuments>#0)"
            ),
            Arguments.of(
                Query.term(String.format("caf%c", (char) 0xE9)),
                String.format("Query(caf%c)", (char) 0xE9)
            ),
            Arguments.of(Query.scale(0, QueryLeafDescriptionTest.FOO), "Query(0 * foo)"),
            Arguments.of(
                Query.scale(1.0 / 3.0, QueryLeafDescriptionTest.FOO),
                "Query(0.33333333333333331483 * foo)"
            ),
            Arguments.of(
                Query.scale(1e20, QueryLeafDescriptionTest.FOO), "Query(1e+20 * foo)"
            )
        );
    }

    static Stream<Arguments> parsed() {
        final Query other = Query.scale(0, Query.term("H2"));
        final Query hone = Query.term("H1");
        final Query jtwo = Query.term("J2");
        final Query apos = Query.term("a", new TermCount(1), new TermPos(1));
        return Stream.of(
            Arguments.of(
                Query.of(
                    QueryOp.NEAR,
                    List.of(
                        QueryLeafDescriptionTest.at("Aorwell", 1),
                        QueryLeafDescriptionTest.at("animal", 2),
                        QueryLeafDescriptionTest.at("farm", 3)
                    ),
                    12
                ),
                "Query((Aorwell@1 NEAR 12 animal@2 NEAR 12 farm@3))"
            ),
            Arguments.of(
                Query.of(
                    QueryOp.PHRASE,
                    List.of(
                        QueryLeafDescriptionTest.at("Aorwell", 1),
                        QueryLeafDescriptionTest.at("animal", 2),
                        QueryLeafDescriptionTest.at("farm", 3)
                    ),
                    12
                ),
                "Query((Aorwell@1 PHRASE 12 animal@2 PHRASE 12 farm@3))"
            ),
            Arguments.of(
                Query.of(
                    QueryOp.NEAR,
                    List.of(
                        QueryLeafDescriptionTest.at("Arichard", 1)
                            .or(QueryLeafDescriptionTest.at("XTrichard", 1)),
                        QueryLeafDescriptionTest.at("XTbook", 2)
                    ),
                    11
                ),
                "Query(((Arichard@1 OR XTrichard@1) NEAR 11 XTbook@2))"
            ),
            Arguments.of(
                Query.of(
                    QueryOp.PHRASE,
                    QueryLeafDescriptionTest.at("Arichard", 1),
                    QueryLeafDescriptionTest.at("Aboulton", 2)
                ).or(
                    Query.of(
                        QueryOp.PHRASE,
                        QueryLeafDescriptionTest.at("XTrichard", 1),
                        QueryLeafDescriptionTest.at("XTboulton", 2)
                    )
                ),
                String.format(
                    "Query(((Arichard@1 PHRASE 2 Aboulton@2) OR %s))",
                    "(XTrichard@1 PHRASE 2 XTboulton@2)"
                )
            ),
            Arguments.of(
                apos.or(
                    QueryLeafDescriptionTest.at("b", 2).andNot(QueryLeafDescriptionTest.at("c", 3))
                ),
                "Query((a@1 OR (b@2 AND_NOT c@3)))"
            ),
            Arguments.of(
                apos.or(
                    QueryLeafDescriptionTest.at("b", 2).xor(QueryLeafDescriptionTest.at("c", 3))
                ),
                "Query((a@1 OR (b@2 XOR c@3)))"
            ),
            Arguments.of(
                apos.xor(
                    QueryLeafDescriptionTest.at("b", 2).andNot(QueryLeafDescriptionTest.at("c", 3))
                ),
                "Query((a@1 XOR (b@2 AND_NOT c@3)))"
            ),
            Arguments.of(
                Query.of(QueryOp.FILTER, apos, hone.and(jtwo)),
                "Query((a@1 FILTER (H1 AND J2)))"
            ),
            Arguments.of(
                Query.of(QueryOp.FILTER, apos, hone.or(Query.term("H2"))),
                "Query((a@1 FILTER (H1 OR H2)))"
            ),
            Arguments.of(
                Query.of(QueryOp.FILTER, apos, hone).and(other),
                "Query(((a@1 FILTER H1) AND 0 * H2))"
            )
        );
    }

    static Stream<Arguments> scaled() {
        final Query zero = Query.scale(0, Query.term("H1"));
        final Query other = Query.scale(0, Query.term("H2"));
        return Stream.of(
            Arguments.of(
                Query.scale(0, Query.term("H1").and(Query.term("J2"))),
                "Query(0 * (H1 AND J2))"
            ),
            Arguments.of(zero.or(other), "Query((0 * H1 OR 0 * H2))"),
            Arguments.of(zero.and(other), "Query((0 * H1 AND 0 * H2))"),
            Arguments.of(
                Query.scale(0, Query.MATCH_ALL), "Query(0 * <alldocuments>)"
            ),
            Arguments.of(
                Query.scale(0, Query.MATCH_ALL)
                    .andNot(QueryLeafDescriptionTest.at("Zwindow", 1)),
                "Query((0 * <alldocuments> AND_NOT Zwindow@1))"
            ),
            Arguments.of(
                Query.scale(0, Query.term("Hworld"))
                    .andNot(QueryLeafDescriptionTest.at("Zcup", 1)),
                "Query((0 * Hworld AND_NOT Zcup@1))"
            ),
            Arguments.of(
                Query.of(
                    QueryOp.AND,
                    List.of(
                        QueryLeafDescriptionTest.at("abc", 1),
                        Query.editDistance("muscl"),
                        QueryLeafDescriptionTest.at("main", 3)
                    )
                ),
                "Query((abc@1 AND EDIT_DISTANCE SYNONYM muscl~2 AND main@3))"
            ),
            Arguments.of(
                Query.of(
                    QueryOp.PHRASE,
                    List.of(
                        QueryLeafDescriptionTest.at("001", 1),
                        QueryLeafDescriptionTest.at("part", 2),
                        QueryLeafDescriptionTest.at("met", 3)
                    )
                ),
                "Query((001@1 PHRASE 3 part@2 PHRASE 3 met@3))"
            ),
            Arguments.of(
                Query.of(
                    QueryOp.PHRASE, QueryLeafDescriptionTest.at("a#", 1),
                    QueryLeafDescriptionTest.at("b", 2)
                ),
                "Query((a#@1 PHRASE 2 b@2))"
            ),
            Arguments.of(
                Query.of(
                    QueryOp.OR,
                    List.of(
                        QueryLeafDescriptionTest.at("13,5", 1),
                        QueryLeafDescriptionTest.at("13", 2),
                        Query.of(
                            QueryOp.PHRASE, QueryLeafDescriptionTest.at("no", 3),
                            QueryLeafDescriptionTest.at("go", 4)
                        )
                    )
                ),
                "Query((13,5@1 OR 13@2 OR (no@3 PHRASE 2 go@4)))"
            ),
            Arguments.of(
                Query.of(
                    QueryOp.PHRASE, QueryLeafDescriptionTest.at("a", 1),
                    QueryLeafDescriptionTest.at("b", 2)
                ).or(
                    QueryLeafDescriptionTest.at("Znear", 3)
                        .or(QueryLeafDescriptionTest.at("Ztoni", 4))
                ),
                "Query(((a@1 PHRASE 2 b@2) OR (Znear@3 OR Ztoni@4)))"
            ),
            Arguments.of(
                Query.of(
                    QueryOp.OR,
                    List.of(
                        QueryLeafDescriptionTest.at("1700+", 1).and(
                            QueryLeafDescriptionTest.at("2100+", 2)
                        ),
                        Query.of(
                            QueryOp.NEAR,
                            List.of(
                                QueryLeafDescriptionTest.at("a", 1),
                                QueryLeafDescriptionTest.at("b", 2),
                                QueryLeafDescriptionTest.at("c", 3)
                            ),
                            8
                        )
                    )
                ),
                "Query(((1700+@1 AND 2100+@2) OR (a@1 NEAR 8 b@2 NEAR 8 c@3)))"
            )
        );
    }

    private static Query at(final String term, final int pos) {
        return Query.term(term, new TermCount(1), new TermPos(pos));
    }
}
