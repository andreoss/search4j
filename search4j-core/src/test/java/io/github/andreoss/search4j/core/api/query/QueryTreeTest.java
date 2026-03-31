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
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of the structure a {@link Query} exposes: types, subqueries, leaf
 * parameters, lengths, terms and equality.
 *
 * @since 0.1.0
 */
final class QueryTreeTest {

    /**
     * A term.
     */
    private static final Query FOO = Query.term("foo");

    /**
     * Another term.
     */
    private static final Query BAR = Query.term("bar");

    @Test
    void leavesHaveTheirTypes() {
        Assertions.assertEquals(
            List.of(
                QueryOp.LEAF_MATCH_ALL, QueryOp.LEAF_MATCH_NOTHING,
                QueryOp.LEAF_TERM, QueryOp.VALUE_RANGE, QueryOp.VALUE_GE,
                QueryOp.VALUE_LE, QueryOp.WILDCARD, QueryOp.EDIT_DISTANCE,
                QueryOp.INVALID, QueryOp.SCALE_WEIGHT
            ),
            List.of(
                Query.MATCH_ALL.type(), Query.MATCH_NOTHING.type(),
                QueryTreeTest.FOO.type(),
                Query.valueRange(new ValueNo(1L), "a", "b").type(),
                Query.value(QueryOp.VALUE_GE, new ValueNo(1L), "a").type(),
                Query.value(QueryOp.VALUE_LE, new ValueNo(1L), "a").type(),
                Query.wildcard("a").type(), Query.editDistance("a").type(),
                Query.invalid().type(), Query.scale(2, QueryTreeTest.FOO).type()
            ),
            "Reference queryintro1 leaf types"
        );
    }

    @Test
    void branchesHaveTheirTypes() {
        Assertions.assertEquals(
            List.of(
                QueryOp.AND_NOT, QueryOp.AND, QueryOp.AND_NOT, QueryOp.OR,
                QueryOp.XOR, QueryOp.NEAR, QueryOp.PHRASE, QueryOp.SYNONYM,
                QueryOp.ELITE_SET
            ),
            List.of(
                Query.of(QueryOp.AND_NOT, Query.MATCH_ALL, QueryTreeTest.FOO).type(),
                QueryTreeTest.FOO.and(QueryTreeTest.BAR).type(),
                QueryTreeTest.BAR.not().type(),
                QueryTreeTest.FOO.or(QueryTreeTest.BAR).type(),
                QueryTreeTest.FOO.xor(QueryTreeTest.BAR).type(),
                Query.of(QueryOp.NEAR, QueryTreeTest.FOO, QueryTreeTest.BAR).type(),
                Query.of(QueryOp.PHRASE, QueryTreeTest.FOO, QueryTreeTest.BAR).type(),
                Query.of(QueryOp.SYNONYM, List.of(QueryTreeTest.FOO.and(QueryTreeTest.BAR)))
                    .type(),
                Query.of(QueryOp.ELITE_SET, QueryTreeTest.FOO, QueryTreeTest.BAR).type()
            ),
            "Reference queryintro1 operator types"
        );
    }

    @Test
    void countsAndReadsSubqueries() {
        final Query diff = Query.of(QueryOp.AND_NOT, Query.MATCH_ALL, Query.term("fair"));
        final Query scaled = Query.scale(1.25, QueryTreeTest.FOO.or(QueryTreeTest.BAR));
        final Query near = Query.of(QueryOp.NEAR, QueryTreeTest.FOO, QueryTreeTest.BAR);
        Assertions.assertEquals(
            List.of(
                0, 0, 2, QueryOp.LEAF_MATCH_ALL, QueryOp.LEAF_TERM, 1, QueryOp.OR,
                2, QueryTreeTest.FOO, QueryTreeTest.BAR, 0
            ),
            List.of(
                Query.MATCH_ALL.subqueryCount(), Query.MATCH_NOTHING.subqueryCount(),
                diff.subqueryCount(), diff.subquery(0).type(), diff.subquery(1).type(),
                scaled.subqueryCount(), scaled.subquery(0).type(),
                near.subqueryCount(), near.subquery(0), near.subquery(1),
                Query.invalid().subqueryCount()
            ),
            "Reference queryintro1 subqueries"
        );
    }

    @Test
    void readsLeafParameters() {
        final Query full = Query.term("foo", new TermCount(2), new TermPos(1));
        Assertions.assertEquals(
            List.of(
                new TermCount(2), new TermPos(1), new TermCount(1), TermPos.FIRST,
                new TermCount(1), TermPos.FIRST
            ),
            List.of(
                full.wqf(), full.pos(), QueryTreeTest.BAR.wqf(), QueryTreeTest.BAR.pos(),
                Query.MATCH_ALL.wqf(), Query.MATCH_ALL.pos()
            ),
            "Reference queryintro1 leaf parameters"
        );
    }

    @Test
    void sumsLengths() {
        final Query first = Query.of(
            QueryOp.AND, QueryTreeTest.FOO.or(QueryTreeTest.BAR),
            Query.term("wibble").or(Query.term("spoon"))
        );
        final Query terms = Query.terms(QueryOp.AND, List.of("foo", "bar", "baz"));
        final Query mixed = Query.of(
            QueryOp.OR,
            List.of(
                Query.term("wibble"), Query.term("wobble"),
                Query.terms(QueryOp.OR, "jelly", "belly")
            )
        );
        final Query pointed = Query.of(
            QueryOp.AND,
            List.of(Query.terms(QueryOp.AND, "ball", "club"), Query.term("ring"))
        );
        final Query joined = Query.of(QueryOp.OR, mixed, pointed);
        Assertions.assertEquals(
            List.of(4, 3, 4, 3, 7, 10, 0, 0, 1, 1, 1, 0, 0, 5, 3),
            List.of(
                first.length().value(), terms.length().value(),
                mixed.length().value(), pointed.length().value(),
                joined.length().value(),
                Query.of(QueryOp.OR, terms, joined).length().value(),
                Query.MATCH_NOTHING.length().value(),
                Query.of(QueryOp.OR, List.of()).length().value(),
                Query.MATCH_ALL.length().value(),
                Query.wildcard("a").length().value(),
                Query.editDistance("a").length().value(),
                Query.valueRange(new ValueNo(1L), "a", "b").length().value(),
                Query.invalid().length().value(),
                Query.term("foo", new TermCount(5)).length().value(),
                Query.scale(2, Query.term("foo", new TermCount(3))).length().value()
            ),
            "Reference querylen1 and querylen2"
        );
    }

    @Test
    void emptyPartsVanishOrAbsorb() {
        final Query none = Query.terms(QueryOp.OR, List.of());
        final Query term = Query.term("x");
        Assertions.assertEquals(
            List.of(true, true, 0, false, 1, 0, false),
            List.of(
                none.empty(), Query.of(QueryOp.AND, none, term).empty(),
                Query.of(QueryOp.AND, none, term).length().value(),
                Query.of(QueryOp.OR, none, term).empty(),
                Query.of(QueryOp.OR, none, term).length().value(),
                Query.MATCH_NOTHING.length().value(), Query.MATCH_ALL.empty()
            ),
            "Reference emptyquery2 and emptyquerypart1"
        );
    }

    @Test
    void listsTermsByPosition() {
        final Query query = Query.of(
            QueryOp.OR,
            List.of(
                Query.term("b", new TermCount(1), new TermPos(2)),
                Query.term("a", new TermCount(1), new TermPos(1)),
                Query.term("b", new TermCount(1), new TermPos(2)),
                Query.term("c", new TermCount(1), new TermPos(3)),
                Query.term("c"), Query.MATCH_ALL,
                Query.scale(2, Query.term("a", new TermCount(1), new TermPos(1))),
                Query.wildcard("z"), Query.editDistance("z"),
                Query.valueRange(new ValueNo(1L), "a", "z")
            )
        );
        Assertions.assertEquals(
            List.of(
                List.of("c", "a", "b", "c"), List.of("a", "b", "c")
            ),
            List.of(query.terms(), query.uniqueTerms()),
            "Positional terms repeat at distinct positions, unique ones do not"
        );
    }

    @Test
    void listsTermsInByteOrder() {
        Assertions.assertEquals(
            List.of(
                "Z", "a", String.valueOf((char) 0xFF5E),
                new String(Character.toChars(0x1F600))
            ),
            Query.of(
                QueryOp.OR,
                List.of(
                    Query.term(new String(Character.toChars(0x1F600))),
                    Query.term(String.valueOf((char) 0xFF5E)),
                    Query.term("Z"), Query.term("a")
                )
            ).uniqueTerms(),
            "Code point order, as the reference compares bytes"
        );
    }

    @Test
    void repeatsPhraseTerms() {
        final Query phrase = Query.of(
            QueryOp.PHRASE,
            List.of(
                Query.term("the", new TermCount(1), new TermPos(1)),
                Query.term("the", new TermCount(1), new TermPos(2)),
                Query.term("the", new TermCount(1), new TermPos(3))
            )
        );
        Assertions.assertEquals(
            List.of(List.of("the", "the", "the"), List.of("the")),
            List.of(phrase.terms(), phrase.uniqueTerms()),
            "Reference queryterms1"
        );
    }

    @Test
    void matchAllAndNothingHaveNoTerms() {
        Assertions.assertEquals(
            List.of(List.of(), List.of(), List.of(), List.of(), List.of("fair")),
            List.of(
                Query.MATCH_ALL.terms(), Query.MATCH_ALL.uniqueTerms(),
                Query.MATCH_NOTHING.terms(), Query.MATCH_NOTHING.uniqueTerms(),
                Query.of(QueryOp.AND_NOT, Query.MATCH_ALL, Query.term("fair")).terms()
            ),
            "Reference queryterms1 and getqterms2"
        );
    }

    @Test
    void equalTreesAreEqual() {
        Assertions.assertEquals(
            List.of(
                Query.term("a"), Query.MATCH_NOTHING, Query.MATCH_ALL,
                Query.term("a").and(Query.term("b")), Query.term("a").hashCode(),
                Query.MATCH_NOTHING.hashCode(), false, true
            ),
            List.of(
                Query.term("a"), Query.of(QueryOp.AND, List.of()),
                Query.term(""), Query.terms(QueryOp.AND, "a", "b"),
                Query.term("a").hashCode(), Query.MATCH_NOTHING.hashCode(),
                Query.term("a").equals(new Object()),
                Query.term("a").equals(Query.term("a"))
            ),
            "Structural equality"
        );
    }

    @Test
    void unequalTreesDiffer() {
        Assertions.assertEquals(
            List.of(false, false, false),
            List.of(
                Query.term("a").equals(Query.term("b")),
                Query.term("a").equals(Query.MATCH_NOTHING),
                Query.MATCH_NOTHING.equals(Query.term("a"))
            ),
            "Different trees"
        );
    }

    @Test
    void printsItsDescription() {
        Assertions.assertEquals(
            "Query((foo AND bar))",
            QueryTreeTest.FOO.and(QueryTreeTest.BAR).toString(),
            "toString is the description"
        );
    }

    @Test
    void wildcardKeepsItsSpec() {
        final Wildcard spec = Wildcard.of("fo").withCombiner(QueryOp.OR);
        Assertions.assertEquals(
            spec, ((WildcardNode) Query.wildcard(spec).node()).spec(),
            "Spec reachable through the node"
        );
    }

    @Test
    void editDistanceKeepsItsSpec() {
        final EditDistance spec = EditDistance.of("fo").withEdits(1);
        Assertions.assertEquals(
            spec, ((EditNode) Query.editDistance(spec).node()).spec(),
            "Spec reachable through the node"
        );
    }
}
