/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the weighted maximum union operator.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Document order and maximum weight of an N-way union.
 *
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.UnitTestContainsTooManyAsserts",
    "PMD.UnnecessaryLocalRule"
})
final class MaxPostListTest {

    @Test
    void mergesDocumentIdsInAscendingOrder() {
        final PostList union = MaxPostList.of(
            List.of(
                BooleanPostListFixture.fake("left", 1, 4),
                BooleanPostListFixture.fake("middle", 2, 3, 5),
                BooleanPostListFixture.fake("right", 6)
            ),
            () -> { }
        );
        Assertions.assertEquals(
            List.of(
                DocId.of(1), DocId.of(2), DocId.of(3),
                DocId.of(4), DocId.of(5), DocId.of(6)
            ),
            MaxPostListTest.drain(union),
            "Union in ascending document order"
        );
    }

    @Test
    void takesTheHighestWeightOfEachDocument() {
        final BooleanPostListFixtureFake left = BooleanPostListFixture.fake(
            "left", 1, 2
        ).scoring(2.0).maxed(9.0);
        final BooleanPostListFixtureFake right = BooleanPostListFixture.fake(
            "right", 2, 3
        ).scoring(7.0).maxed(9.0);
        final PostList union = MaxPostList.of(
            List.of(left, right), () -> { }
        );
        Assertions.assertEquals(
            List.of(2.0, 7.0),
            List.of(
                union.skipTo(DocId.of(1), 0.0).weight(
                    BooleanPostListFixture.doc()
                ),
                union.skipTo(DocId.of(2), 0.0).weight(
                    BooleanPostListFixture.doc()
                )
            ),
            "Maximum weight per document"
        );
        Assertions.assertEquals(
            9.0, union.recalcMaxWeight(), 1.0e-9, "Maximum weight bound"
        );
    }

    @Test
    void reportsFrequencyAndDescription() {
        final PostList union = MaxPostList.of(
            List.of(
                BooleanPostListFixture.fake("left", 1, 4),
                BooleanPostListFixture.fake("right", 2)
            ),
            () -> { }
        );
        Assertions.assertEquals(
            3, union.termFrequency().value(), "Estimate of independent children"
        );
        Assertions.assertEquals(
            "(left MAX right)", union.description(), "Description"
        );
        Assertions.assertEquals(
            new TermCount(1), union.countMatchingSubqueries(), "One subquery"
        );
    }

    @Test
    void estimatesFrequencyOverTheDatabaseAndGivesWayToTheLastChild() {
        final PostList right = BooleanPostListFixture.fake("right", 2, 3);
        final PostList union = new MaxPostList(
            List.of(BooleanPostListFixture.fake("left", 1, 2, 4), right),
            () -> { }, new TermCount(4)
        );
        Assertions.assertEquals(
            4, union.termFrequency().value(), "Independent children in four documents"
        );
        PostList list = union.next(0.0);
        list = list.next(0.0);
        list = list.next(0.0);
        final DocId third = list.docId();
        final PostList last = list.next(0.0);
        Assertions.assertEquals(
            List.of(DocId.of(3), true, DocId.of(4)),
            List.of(third, !last.equals(union), last.docId()),
            "The left child is left alone after the right one ends"
        );
    }

    @Test
    void rejectsInvalidConstruction() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> MaxPostList.of(List.of(), () -> { }),
            "At least one child"
        );
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> MaxPostList.of(List.of(), null),
            "Callback is required"
        );
    }

    private static List<DocId> drain(final PostList source) {
        PostList list = source;
        final List<DocId> result = new ArrayList<>(0);
        list = list.next();
        while (!list.atEnd()) {
            result.add(list.docId());
            list = list.next();
        }
        return result;
    }
}
