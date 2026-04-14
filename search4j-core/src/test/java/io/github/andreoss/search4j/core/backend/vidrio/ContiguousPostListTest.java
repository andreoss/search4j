/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link ContiguousPostList}.
 *
 * @since 0.9.1
 */
final class ContiguousPostListTest {

    @Test
    void listsEveryIdentifierInOrder() {
        final ContiguousPostList list = new ContiguousPostList(new TermCount(4));
        final List<Integer> seen = new ArrayList<>(4);
        list.next(0.0);
        while (!list.atEnd()) {
            seen.add(list.docId().value());
            list.next(0.0);
        }
        Assertions.assertEquals(List.of(1, 2, 3, 4), seen, "Identifiers 1 to 4");
    }

    @Test
    void skipsForwardOnly() {
        final ContiguousPostList list = new ContiguousPostList(new TermCount(9));
        final List<Object> seen = new ArrayList<>(5);
        list.skipTo(DocId.of(4), 0.0);
        seen.add(list.docId().value());
        list.skipTo(DocId.of(2), 0.0);
        seen.add(list.docId().value());
        list.skipTo(DocId.of(9), 0.0);
        seen.add(list.docId().value());
        seen.add(list.atEnd());
        list.skipTo(DocId.of(10), 0.0);
        seen.add(list.atEnd());
        Assertions.assertEquals(
            List.of(4, 4, 9, false, true), seen, "Skips never go back"
        );
    }

    @Test
    void movesInPlace() {
        final ContiguousPostList list = new ContiguousPostList(new TermCount(3));
        Assertions.assertAll(
            () -> Assertions.assertSame(list, list.next(0.0), "Next"),
            () -> Assertions.assertSame(
                list, list.skipTo(DocId.of(2), 0.0), "Skip"
            )
        );
    }

    @Test
    void endsAfterLastIdentifier() {
        final ContiguousPostList list = new ContiguousPostList(new TermCount(1));
        list.next(0.0);
        final boolean first = list.atEnd();
        list.next(0.0);
        Assertions.assertEquals(
            List.of(false, true), List.of(first, list.atEnd()), "One identifier"
        );
    }

    @Test
    void describesItsStatistics() {
        final ContiguousPostList list = new ContiguousPostList(new TermCount(7));
        list.next(0.0);
        Assertions.assertEquals(
            List.of(
                "ContiguousPostList(1..7)", new TermCount(7), new TermCount(7),
                TermCount.ONE, TermCount.ONE, false, ""
            ),
            List.of(
                list.description(), list.termFrequency(),
                list.collectionFrequency(), list.wdf(), list.wdfUpperBound(),
                list.openNearby("a", false).isPresent(), list.term()
            ),
            "Every document once"
        );
    }

    @Test
    void rejectsReadsOffAnIdentifier() {
        final ContiguousPostList list = new ContiguousPostList(new TermCount(2));
        Assertions.assertAll(
            () -> Assertions.assertThrows(
                InvalidOperationError.class, list::docId, "Before the first"
            ),
            () -> Assertions.assertThrows(
                InvalidOperationError.class, list::wdf, "No frequency"
            ),
            () -> Assertions.assertThrows(
                InvalidOperationError.class, list::readPositionList, "No positions"
            ),
            () -> Assertions.assertThrows(
                InvalidOperationError.class, list::openPositionList, "No positions"
            )
        );
    }
}
