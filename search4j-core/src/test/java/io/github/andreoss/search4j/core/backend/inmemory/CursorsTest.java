/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the in-memory database backend.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.api.PositionIterator;
import io.github.andreoss.search4j.core.api.PostingIterator;
import io.github.andreoss.search4j.core.api.TermIterator;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of the iterator building blocks of the in-memory backend.
 *
 * @since 0.1.0
 */
final class CursorsTest {

    @Test
    void positionsSkipToPresentTarget() {
        try (PositionIterator iter = new PositionCursor(Corpus.at(1, 4, 9))) {
            iter.skipTo(new TermPos(4));
            Assertions.assertEquals(new TermPos(4), iter.next(), "Exact hit");
        }
    }

    @Test
    void positionsSkipToAbsentTarget() {
        try (PositionIterator iter = new PositionCursor(Corpus.at(1, 4, 9))) {
            iter.skipTo(new TermPos(5));
            Assertions.assertEquals(new TermPos(9), iter.next(), "Next above");
        }
    }

    @Test
    void positionsNeverSkipBackwards() {
        try (PositionIterator iter = new PositionCursor(Corpus.at(1, 4, 9))) {
            iter.next();
            iter.next();
            iter.skipTo(new TermPos(1));
            Assertions.assertEquals(new TermPos(9), iter.next(), "Forward");
        }
    }

    @Test
    void positionsSkipPastEnd() {
        try (PositionIterator iter = new PositionCursor(Corpus.at(1, 4))) {
            iter.skipTo(new TermPos(5));
            Assertions.assertFalse(iter.hasNext(), "Past the end");
        }
    }

    @Test
    void positionsEndRaises() {
        try (PositionIterator iter = new PositionCursor(Corpus.at())) {
            Assertions.assertThrows(
                NoSuchElementException.class, iter::next, "Empty"
            );
        }
    }

    @Test
    void closedPositionsEnd() {
        final PositionIterator iter = new PositionCursor(Corpus.at(1));
        iter.close();
        Assertions.assertFalse(iter.hasNext(), "Closed iterator ends");
    }

    @Test
    void postingEndRaises() {
        try (
            InMemoryDatabase db = Corpus.build();
            PostingIterator iter = db.postings("cherry")
        ) {
            iter.next();
            Assertions.assertThrows(
                NoSuchElementException.class, iter::next, "At the end"
            );
        }
    }

    @Test
    void postingAccessorBeforeStartFails() {
        try (
            InMemoryDatabase db = Corpus.build();
            PostingIterator iter = db.postings("cherry")
        ) {
            Assertions.assertThrows(
                InvalidOperationError.class, iter::wdf, "Not started"
            );
        }
    }

    @Test
    void everyDocumentWdfBeforeStartFails() {
        try (
            InMemoryDatabase db = Corpus.build();
            PostingIterator iter = db.postings("")
        ) {
            Assertions.assertThrows(
                InvalidOperationError.class, iter::wdf, "Not started"
            );
        }
    }

    @Test
    void termSkipNeverGoesBackwards() {
        try (
            InMemoryDatabase db = Corpus.build();
            TermIterator iter = db.termList(DocId.of(1))
        ) {
            iter.next();
            iter.skipTo("a");
            Assertions.assertEquals("banana", iter.next(), "Forward only");
        }
    }

    @Test
    void termSkipToCurrentStays() {
        try (
            InMemoryDatabase db = Corpus.build();
            TermIterator iter = db.termList(DocId.of(1))
        ) {
            iter.next();
            iter.skipTo("apple");
            Assertions.assertEquals("banana", iter.next(), "Past current");
        }
    }

    @Test
    void orderComparesCodePoints() {
        Assertions.assertTrue(
            TermOrder.INSTANCE.compare("￿", "𐀀") < 0,
            "Supplementary after BMP"
        );
    }

    @Test
    void orderPutsPrefixFirst() {
        Assertions.assertTrue(
            TermOrder.INSTANCE.compare("ab", "abc") < 0, "Prefix first"
        );
    }

    @Test
    void orderPutsLongerLast() {
        Assertions.assertTrue(
            TermOrder.INSTANCE.compare("abc", "ab") > 0, "Longer last"
        );
    }

    @Test
    void orderFindsEqual() {
        Assertions.assertEquals(
            0, TermOrder.INSTANCE.compare("same", "same"), "Equal"
        );
    }

    @Test
    void orderSortsList() {
        final List<String> sorted = new ArrayList<>(
            List.of("b", "😀", "a", "ａ")
        );
        sorted.sort(TermOrder.INSTANCE);
        Assertions.assertEquals(
            List.of("a", "b", "ａ", "😀"), sorted, "Sorted"
        );
    }
}
