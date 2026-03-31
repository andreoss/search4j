/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link PendingPositions}.
 *
 * @since 0.1.0
 */
final class PendingPositionsTest {

    /**
     * Document of most tests.
     */
    private static final DocId DOC = DocId.of(7);

    @Test
    void keepsEncodedListsUntilFlushed() {
        final PendingPositions pending = new PendingPositions();
        pending.set(PendingPositionsTest.DOC, "apple", Positions.list(1, 5, 9));
        pending.delete(PendingPositionsTest.DOC, "pear");
        Assertions.assertEquals(
            List.of(true, new TermCount(3), 0, false),
            List.of(
                pending.pending(),
                VidrioPositionList.of(
                    pending.get(PendingPositionsTest.DOC, "apple").orElseThrow()
                ).approxSize(),
                pending.get(PendingPositionsTest.DOC, "pear").orElseThrow().length,
                pending.get(DocId.of(8), "apple").isPresent()
            ),
            "Set list, removed list, unknown pair"
        );
    }

    @Test
    void refusesPositionZero() {
        final PendingPositions pending = new PendingPositions();
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> pending.set(PendingPositionsTest.DOC, "apple", Positions.list(0, 1)),
            "Position 0"
        );
        Assertions.assertFalse(pending.pending(), "Nothing recorded");
    }

    @Test
    void flushesSetsAndRemovals() {
        final MemoryBlocks blocks = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        final TableWriter table = TableWriter.create(blocks, 0);
        new PositionWriter(table).set(PendingPositionsTest.DOC, "pear", Positions.list(2));
        final PendingPositions pending = new PendingPositions();
        pending.set(PendingPositionsTest.DOC, "apple", Positions.list(1, 5));
        pending.delete(PendingPositionsTest.DOC, "pear");
        pending.flush(table);
        final PositionTable committed = PositionTable.of(
            VidrioTable.over(blocks, table.commit(new RevisionNumber(1L)).root())
        );
        Assertions.assertEquals(
            List.of(new TermCount(2), false, false),
            List.of(
                committed.count(PendingPositionsTest.DOC, "apple"),
                committed.contains(PendingPositionsTest.DOC, "pear"),
                pending.pending()
            ),
            "Written and forgotten"
        );
    }

    @Test
    void storeSkipsListsEqualToStoredOnes() {
        final TableWriter table = TableWriter.create(
            new MemoryBlocks(new HashMap<>(), BlockSize.MIN), 0
        );
        new PositionWriter(table).set(
            PendingPositionsTest.DOC, "apple", Positions.list(1, 5)
        );
        final PendingPositions pending = new PendingPositions();
        pending.store(
            table, new PositionKey(PendingPositionsTest.DOC, "apple"),
            Positions.list(1, 5)
        );
        final boolean same = pending.pending();
        pending.store(
            table, new PositionKey(PendingPositionsTest.DOC, "apple"),
            Positions.list(1, 6)
        );
        pending.store(
            table, new PositionKey(PendingPositionsTest.DOC, "apple"),
            Positions.list(1, 7)
        );
        pending.store(
            table, new PositionKey(PendingPositionsTest.DOC, "pear"), List.of()
        );
        Assertions.assertEquals(
            List.of(false, true, 0, true),
            List.of(
                same,
                VidrioPositionList.of(
                    pending.get(PendingPositionsTest.DOC, "apple").orElseThrow()
                ).skipTo(new TermPos(7)),
                pending.get(PendingPositionsTest.DOC, "pear").orElseThrow().length,
                pending.hasPositions(EntryCount.EMPTY)
            ),
            "Equal list skipped, pending list replaced, empty list removes"
        );
    }

    @Test
    void reportsPositionsFromPendingAndStoredLists() {
        final boolean none = new PendingPositions().hasPositions(EntryCount.EMPTY);
        final PendingPositions pending = new PendingPositions();
        final boolean stored = pending.hasPositions(new EntryCount(2L));
        pending.delete(PendingPositionsTest.DOC, "apple");
        final boolean left = pending.hasPositions(new EntryCount(2L));
        pending.delete(PendingPositionsTest.DOC, "pear");
        final boolean gone = pending.hasPositions(new EntryCount(2L));
        pending.set(PendingPositionsTest.DOC, "plum", Positions.list(3));
        final boolean added = pending.hasPositions(EntryCount.EMPTY);
        pending.clear();
        Assertions.assertEquals(
            List.of(false, true, true, false, true, false),
            List.of(
                none, stored, left, gone, added,
                pending.hasPositions(EntryCount.EMPTY)
            ),
            "Reference has_positions cases"
        );
    }
}
