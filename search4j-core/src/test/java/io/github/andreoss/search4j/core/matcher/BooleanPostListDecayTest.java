/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the boolean posting list operators.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.types.DocId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of the positions reached when pruning by the minimum weight decays
 * an optional intersection and starts a difference.
 *
 * @since 0.5.2
 */
final class BooleanPostListDecayTest {

    @Test
    void decayKeepsTheDocumentTheOptionalSideReached() {
        final AndMaybePostList list = new AndMaybePostList(
            BooleanPostListFixture.fake("A", 1, 3, 5).scoring(1.0).maxed(1.0),
            new BooleanPostListFixtureEager(
                BooleanPostListFixture.fake("B", 3, 5)
                    .scoring(2.0).maxed(2.0),
                DocId.of(3)
            ),
            () -> { }
        );
        list.recalcMaxWeight();
        list.next(0.0);
        Assertions.assertEquals(
            DocId.of(3), list.next(2.5).docId(),
            "Document matching both sides after the optional one"
        );
    }

    @Test
    void decayFromTheStartReachesTheFirstDocument() {
        final AndMaybePostList list = new AndMaybePostList(
            BooleanPostListFixture.fake("A", 1, 3).scoring(1.0).maxed(1.0),
            BooleanPostListFixture.fake("B", 1, 3).scoring(2.0).maxed(2.0),
            () -> { }
        );
        list.recalcMaxWeight();
        Assertions.assertEquals(
            DocId.of(1), list.next(2.5).docId(), "First document of both"
        );
    }

    @Test
    void unstartedDifferenceExcludesItsFirstDocument() {
        Assertions.assertEquals(
            DocId.of(3),
            new AndNotPostList(
                new BooleanPostListFixtureEager(
                    BooleanPostListFixture.fake("A", 1, 3), DocId.of(1)
                ),
                BooleanPostListFixture.fake("B", 1),
                BooleanPostListFixture.size()
            ).skipTo(DocId.of(1), 0.0).docId(),
            "First document not excluded"
        );
    }

    @Test
    void decayOverAnUnstartedDifferenceExcludesItsFirstDocument() {
        final AndMaybePostList list = new AndMaybePostList(
            new AndNotPostList(
                new BooleanPostListFixtureEager(
                    BooleanPostListFixture.fake("A", 1, 3, 5)
                        .scoring(1.0).maxed(1.0),
                    DocId.of(1)
                ),
                BooleanPostListFixture.fake("B", 1),
                BooleanPostListFixture.size()
            ),
            BooleanPostListFixture.fake("C", 1, 3).scoring(2.0).maxed(2.0),
            () -> { }
        );
        list.recalcMaxWeight();
        Assertions.assertEquals(
            DocId.of(3), list.next(2.5).docId(),
            "First document matching the difference and the optional side"
        );
    }
}
