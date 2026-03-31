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
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link EditDistance}.
 *
 * @since 0.1.0
 */
final class EditDistanceTest {

    @Test
    void startsFromDefaults() {
        Assertions.assertEquals(
            new EditDistance("possum", Expansion.UNLIMITED, QueryOp.SYNONYM, 2, 0),
            EditDistance.of("possum"),
            "Unlimited, synonyms, two edits, no fixed prefix"
        );
    }

    @Test
    void changesOneFieldAtATime() {
        final Expansion limit =
            new Expansion(new TermCount(3), WildcardLimit.MOST_FREQUENT);
        Assertions.assertEquals(
            List.of(
                new EditDistance("possum", limit, QueryOp.SYNONYM, 2, 0),
                new EditDistance("possum", Expansion.UNLIMITED, QueryOp.OR, 2, 0),
                new EditDistance("possum", Expansion.UNLIMITED, QueryOp.SYNONYM, 3, 0),
                new EditDistance("possum", Expansion.UNLIMITED, QueryOp.SYNONYM, 2, 1)
            ),
            List.of(
                EditDistance.of("possum").withExpansion(limit),
                EditDistance.of("possum").withCombiner(QueryOp.OR),
                EditDistance.of("possum").withEdits(3),
                EditDistance.of("possum").withPrefix(1)
            ),
            "Each change keeps the rest"
        );
    }

    @Test
    void rejectsOtherCombiners() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> EditDistance.of("possum").withCombiner(QueryOp.AND_NOT),
            "Only SYNONYM, MAX and OR combine"
        );
    }

    @Test
    void rejectsNegativeEdits() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> EditDistance.of("possum").withEdits(-1),
            "Edits are a count"
        );
    }

    @Test
    void rejectsNegativePrefix() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> EditDistance.of("possum").withPrefix(-1),
            "The prefix is a length"
        );
    }

    @Test
    void rejectsNullTarget() {
        Assertions.assertThrows(
            NullPointerException.class, () -> EditDistance.of(null),
            "A target is required"
        );
    }

    @Test
    void rejectsNullExpansion() {
        Assertions.assertThrows(
            NullPointerException.class,
            () -> EditDistance.of("possum").withExpansion(null),
            "An expansion is required"
        );
    }
}
