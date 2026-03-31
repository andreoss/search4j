/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the query trees.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.WildcardLimit;
import io.github.andreoss.search4j.core.types.WildcardPattern;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link Wildcard} and {@link Expansion}.
 *
 * @since 0.1.0
 */
final class WildcardTest {

    @Test
    void startsFromDefaults() {
        Assertions.assertEquals(
            new Wildcard(
                "fo", Expansion.UNLIMITED, WildcardPattern.PREFIX, QueryOp.SYNONYM
            ),
            Wildcard.of("fo"),
            "Unlimited literal prefix combined as synonyms"
        );
    }

    @Test
    void changesOneFieldAtATime() {
        final Expansion limit = new Expansion(new TermCount(6), WildcardLimit.FIRST);
        Assertions.assertEquals(
            List.of(
                new Wildcard("fo", limit, WildcardPattern.PREFIX, QueryOp.SYNONYM),
                new Wildcard(
                    "fo", Expansion.UNLIMITED, WildcardPattern.GLOB, QueryOp.SYNONYM
                ),
                new Wildcard(
                    "fo", Expansion.UNLIMITED, WildcardPattern.PREFIX, QueryOp.MAX
                )
            ),
            List.of(
                Wildcard.of("fo").withExpansion(limit),
                Wildcard.of("fo").withGlob(WildcardPattern.GLOB),
                Wildcard.of("fo").withCombiner(QueryOp.MAX)
            ),
            "Each change keeps the rest"
        );
    }

    @Test
    void rejectsOtherCombiners() {
        Assertions.assertEquals(
            "combiner must be SYNONYM or MAX or OR",
            Assertions.assertThrows(
                InvalidArgumentError.class,
                () -> Wildcard.of("fo").withCombiner(QueryOp.AND),
                "Only SYNONYM, MAX and OR combine"
            ).getMessage(),
            "Message"
        );
    }

    @Test
    void rejectsXorCombiner() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> Wildcard.of("fo").withCombiner(QueryOp.XOR),
            "Only SYNONYM, MAX and OR combine"
        );
    }

    @Test
    void rejectsNullPattern() {
        Assertions.assertThrows(
            NullPointerException.class, () -> Wildcard.of(null),
            "A pattern is required"
        );
    }

    @Test
    void rejectsNullExpansion() {
        Assertions.assertThrows(
            NullPointerException.class, () -> Wildcard.of("fo").withExpansion(null),
            "An expansion is required"
        );
    }

    @Test
    void rejectsNullGlob() {
        Assertions.assertThrows(
            NullPointerException.class, () -> Wildcard.of("fo").withGlob(null),
            "Glob features are required"
        );
    }

    @Test
    void unlimitedExpansionRaisesAtZero() {
        Assertions.assertEquals(
            new Expansion(TermCount.ZERO, WildcardLimit.ERROR), Expansion.UNLIMITED,
            "No limit"
        );
    }

    @Test
    void expansionRequiresItsLimit() {
        Assertions.assertThrows(
            NullPointerException.class,
            () -> new Expansion(TermCount.ZERO, null),
            "A limit action is required"
        );
    }

    @Test
    void expansionRequiresItsCount() {
        Assertions.assertThrows(
            NullPointerException.class,
            () -> new Expansion(null, WildcardLimit.ERROR),
            "A count is required"
        );
    }
}
