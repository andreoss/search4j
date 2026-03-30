/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the iterator bases shared by the database backends.
 */
package io.github.andreoss.search4j.core.backend.list;

import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link EmptyPostList}, {@link EmptyTermList} and the term
 * listing defaults.
 *
 * @since 0.1.0
 */
final class EmptyListsTest {

    @Test
    void postListIsAtEnd() {
        Assertions.assertTrue(EmptyPostList.INSTANCE.atEnd(), "At end");
    }

    @Test
    void postListStaysOnNext() {
        Assertions.assertSame(
            EmptyPostList.INSTANCE, EmptyPostList.INSTANCE.next(1.0), "Next"
        );
    }

    @Test
    void postListStaysOnSkip() {
        Assertions.assertSame(
            EmptyPostList.INSTANCE,
            EmptyPostList.INSTANCE.skipTo(new DocId(5), 1.0),
            "Skip"
        );
    }

    @Test
    void postListHasNoDocument() {
        Assertions.assertThrows(
            InvalidOperationError.class,
            EmptyPostList.INSTANCE::docId,
            "No document"
        );
    }

    @Test
    void postListHasNoFrequency() {
        Assertions.assertEquals(
            TermCount.ZERO, EmptyPostList.INSTANCE.termFrequency(), "Freq"
        );
    }

    @Test
    void postListScoresNothing() {
        Assertions.assertEquals(
            0.0,
            EmptyPostList.INSTANCE.weight(
                new DocStats(TermCount.ZERO, TermCount.ZERO, TermCount.ZERO)
            ),
            "Weight"
        );
    }

    @Test
    void postListHasNoMaxWeight() {
        Assertions.assertEquals(
            0.0, EmptyPostList.INSTANCE.recalcMaxWeight(), "Max"
        );
    }

    @Test
    void postListCountsNoSubquery() {
        Assertions.assertEquals(
            TermCount.ZERO,
            EmptyPostList.INSTANCE.countMatchingSubqueries(),
            "Subqueries"
        );
    }

    @Test
    void postListDescribesItself() {
        Assertions.assertEquals(
            "EmptyPostList", EmptyPostList.INSTANCE.description(), "Name"
        );
    }

    @Test
    void termListIsAtEnd() {
        Assertions.assertTrue(EmptyTermList.INSTANCE.atEnd(), "At end");
    }

    @Test
    void termListStaysOnNext() {
        Assertions.assertSame(
            EmptyTermList.INSTANCE, EmptyTermList.INSTANCE.next(), "Next"
        );
    }

    @Test
    void termListStaysOnSkip() {
        Assertions.assertSame(
            EmptyTermList.INSTANCE, EmptyTermList.INSTANCE.skipTo("m"), "Skip"
        );
    }

    @Test
    void termListHasNoSize() {
        Assertions.assertEquals(
            TermCount.ZERO, EmptyTermList.INSTANCE.approxSize(), "Size"
        );
    }

    @Test
    void termListHoldsNoPositions() {
        Assertions.assertTrue(
            EmptyTermList.INSTANCE.heldPositions().isEmpty(), "No positions"
        );
    }

    @Test
    void termListHasNoTerm() {
        Assertions.assertThrows(
            InvalidOperationError.class, EmptyTermList.INSTANCE::term, "Term"
        );
    }

    @Test
    void termListHasNoFrequency() {
        Assertions.assertThrows(
            InvalidOperationError.class,
            EmptyTermList.INSTANCE::termFrequency,
            "No frequency"
        );
    }

    @Test
    void termListingHasNoWdf() {
        Assertions.assertThrows(
            InvalidOperationError.class, EmptyTermList.INSTANCE::wdf, "Wdf"
        );
    }

    @Test
    void termListingHasNoPositionCount() {
        Assertions.assertThrows(
            InvalidOperationError.class,
            EmptyTermList.INSTANCE::positionCount,
            "No count"
        );
    }

    @Test
    void termListingHasNoPositions() {
        Assertions.assertThrows(
            InvalidOperationError.class,
            EmptyTermList.INSTANCE::positionList,
            "No positions"
        );
    }
}
