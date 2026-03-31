/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the query trees.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.types.QueryOp;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Descriptions of plans built directly, including the shapes the flattening
 * step does not produce.
 *
 * @since 0.1.0
 */
final class PlanTest {

    @Test
    void describesEmptyContexts() {
        final OrPlan none = new OrPlan(OrPlan.Kind.OR, List.of(), List.of());
        Assertions.assertEquals(
            List.of("NOTHING", "NOTHING", "(<alldocuments>:0 PHRASE 0-2 window 2)"),
            List.of(
                none.describe(),
                new AndPlan(List.of(), false, none, List.of(), none).describe(),
                new AndPlan(
                    List.of(), true, none,
                    List.of(new AndPlan.Window(QueryOp.PHRASE, 0, 2, 2)), none
                ).describe()
            ),
            "Contexts without members"
        );
    }

    @Test
    void describesASingleMemberWithASelection() {
        Assertions.assertEquals(
            "(XOR(a:1) ELITE 0 of 0-1)",
            new OrPlan(
                OrPlan.Kind.XOR,
                List.of(new LeafPlan(Query.term("a").node(), 1.0)),
                List.of(new OrPlan.Elite(0, 0, 1))
            ).describe(),
            "A selection keeps the union shape"
        );
    }

    @Test
    void keepsEveryMemberWhenTheSetIsLargeEnough() {
        Assertions.assertEquals(
            "OR(a:1, b:1, c:1)",
            Planner.plan(
                Query.of(
                    QueryOp.ELITE_SET,
                    List.of(Query.term("a"), Query.term("b"), Query.term("c")), 3
                ),
                1.0
            ).describe(),
            "A set as large as its members selects nothing"
        );
    }

    @Test
    void rejectsMissingParts() {
        Assertions.assertAll(
            () -> Assertions.assertThrows(
                NullPointerException.class, () -> new LeafPlan(null, 1.0), "leaf"
            ),
            () -> Assertions.assertThrows(
                NullPointerException.class, () -> new PositionsPlan(null), "positions"
            ),
            () -> Assertions.assertThrows(
                NullPointerException.class, () -> new SynonymPlan(1.0, null), "synonym"
            ),
            () -> Assertions.assertThrows(
                NullPointerException.class,
                () -> new OrPlan(null, List.of(), List.of()), "union"
            ),
            () -> Assertions.assertThrows(
                NullPointerException.class,
                () -> new AndPlan(List.of(), false, null, List.of(), null), "meet"
            )
        );
    }
}
