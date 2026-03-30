/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the iterator bases shared by the database backends.
 */
package io.github.andreoss.search4j.core.backend.list;

import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link VectorPositionList}.
 *
 * @since 0.1.0
 */
final class VectorPositionListTest {

    @Test
    void walksPositions() {
        final PositionList list = VectorPositionListTest.of(2, 5, 9);
        final List<TermPos> seen = new ArrayList<>(0);
        while (list.next()) {
            seen.add(list.position());
        }
        Assertions.assertEquals(
            List.of(new TermPos(2), new TermPos(5), new TermPos(9)),
            seen,
            "Every position"
        );
    }

    @Test
    void staysAtEnd() {
        final PositionList list = VectorPositionListTest.of(1);
        list.next();
        list.next();
        Assertions.assertFalse(list.next(), "Still ended");
    }

    @Test
    void rejectsPositionAtEnd() {
        final PositionList list = VectorPositionListTest.of(1);
        list.next();
        list.next();
        Assertions.assertThrows(
            InvalidOperationError.class, list::position, "No position at end"
        );
    }

    @Test
    void rejectsPositionBeforeStart() {
        Assertions.assertThrows(
            InvalidOperationError.class,
            VectorPositionListTest.of(1)::position,
            "No position before start"
        );
    }

    @Test
    void countsPositions() {
        Assertions.assertEquals(
            new TermCount(3),
            VectorPositionListTest.of(1, 2, 3).approxSize(),
            "Size"
        );
    }

    @Test
    void knowsLastPosition() {
        Assertions.assertEquals(
            new TermPos(7), VectorPositionListTest.of(3, 7).back(), "Last"
        );
    }

    @Test
    void hasFirstAsLastOfEmpty() {
        Assertions.assertEquals(
            TermPos.FIRST, VectorPositionListTest.of().back(), "Empty back"
        );
    }

    @Test
    void endsAtOnceWhenEmpty() {
        Assertions.assertFalse(VectorPositionListTest.of().next(), "Empty");
    }

    @Test
    void skipsToExactPosition() {
        final PositionList list = VectorPositionListTest.of(2, 5, 9);
        Assertions.assertEquals(
            "true 5",
            String.format(
                "%b %s", list.skipTo(new TermPos(5)), list.position()
            ),
            "On it"
        );
    }

    @Test
    void skipsToNextHigher() {
        final PositionList list = VectorPositionListTest.of(2, 5, 9);
        Assertions.assertEquals(
            "true 9",
            String.format(
                "%b %s", list.skipTo(new TermPos(6)), list.position()
            ),
            "On next"
        );
    }

    @Test
    void skipsFromStartToFirst() {
        final PositionList list = VectorPositionListTest.of(2, 5);
        Assertions.assertEquals(
            "true 2",
            String.format(
                "%b %s", list.skipTo(new TermPos(0)), list.position()
            ),
            "On first"
        );
    }

    @Test
    void neverSkipsBackwards() {
        final PositionList list = VectorPositionListTest.of(2, 5, 9);
        list.skipTo(new TermPos(9));
        Assertions.assertEquals(
            "true 9",
            String.format(
                "%b %s", list.skipTo(new TermPos(2)), list.position()
            ),
            "Kept"
        );
    }

    @Test
    void skipsPastEnd() {
        final PositionList list = VectorPositionListTest.of(2, 5);
        Assertions.assertEquals(
            "false false",
            String.format("%b %b", list.skipTo(new TermPos(6)), list.next()),
            "Past end"
        );
    }

    @Test
    void movesOnAfterSkip() {
        final PositionList list = VectorPositionListTest.of(2, 5, 9);
        list.skipTo(new TermPos(5));
        Assertions.assertEquals(
            "true 9",
            String.format("%b %s", list.next(), list.position()),
            "After skip"
        );
    }

    @Test
    void copiesPositions() {
        final List<TermPos> source = new ArrayList<>(List.of(new TermPos(4)));
        final PositionList list = new VectorPositionList(source);
        source.add(new TermPos(8));
        Assertions.assertEquals(new TermCount(1), list.approxSize(), "Copied");
    }

    private static PositionList of(final int... positions) {
        final List<TermPos> list = new ArrayList<>(positions.length);
        for (final int position : positions) {
            list.add(new TermPos(position));
        }
        return new VectorPositionList(list);
    }
}
