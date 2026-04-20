/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.TermFrequencies;
import io.github.andreoss.search4j.core.error.DocNotFoundError;
import io.github.andreoss.search4j.core.error.InvariantError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link Inverter} and {@link PendingLengths}.
 *
 * @since 0.1.0
 */
final class InverterTest {

    @Test
    void appliesDeltasToStoredFrequencies() {
        final Inverter inverter = new Inverter();
        inverter.addPosting(DocId.of(1), "apple", new TermCount(3));
        inverter.addPosting(DocId.of(2), "apple", new TermCount(2));
        inverter.removePosting(DocId.of(3), "apple", new TermCount(4));
        inverter.updatePosting(
            DocId.of(4), "apple", new WdfChange(new TermCount(1), new TermCount(6))
        );
        final TermFrequencies stored =
            new TermFrequencies(new TermCount(10), new TermCount(20));
        Assertions.assertEquals(
            List.of(
                new TermFrequencies(new TermCount(11), new TermCount(26)), stored
            ),
            List.of(
                inverter.frequencies("apple", stored),
                inverter.frequencies("pear", stored)
            ),
            "Term frequency plus one, collection frequency plus six"
        );
    }

    @Test
    void wrapsFrequenciesBelowZeroAsTheReference() {
        final Inverter inverter = new Inverter();
        inverter.removePosting(DocId.of(1), "apple", new TermCount(5));
        Assertions.assertEquals(
            new TermFrequencies(TermCount.MAX, TermCount.parse("4294967291")),
            inverter.frequencies("apple", TermFrequencies.NONE),
            "Below zero wraps"
        );
    }

    @Test
    void wrapsCollectionFrequencyAsTheReference() {
        final Inverter large = new Inverter();
        large.addPosting(DocId.of(2), "apple", TermCount.parse("3000000000"));
        Assertions.assertEquals(
            List.of(
                new TermFrequencies(
                    new TermCount(2), TermCount.parse("3000000001")
                ),
                new TermFrequencies(
                    new TermCount(2), TermCount.parse("1705032704")
                )
            ),
            List.of(
                large.frequencies(
                    "apple",
                    new TermFrequencies(new TermCount(1), new TermCount(1))
                ),
                large.frequencies(
                    "apple",
                    new TermFrequencies(
                        new TermCount(1), TermCount.parse("3000000000")
                    )
                )
            ),
            "Counts above the signed range, sums modulo two to the 32"
        );
    }

    @Test
    void flushesTermsByPrefixInOrder() {
        final Inverter inverter = new Inverter();
        for (final String term : List.of("bb", "ba", "b", "c", "a", "bé")) {
            inverter.addPosting(DocId.of(1), term, new TermCount(1));
        }
        final MemoryBlocks blocks = InverterTest.blocks();
        final TableWriter table = TableWriter.create(blocks, 0);
        final PostingWriter writer = new PostingWriter(table);
        inverter.flushPrefix(writer, "b");
        inverter.flushTerm(writer, "c");
        inverter.flushTerm(writer, "missing");
        final PostingTable committed = InverterTest.committed(blocks, table);
        Assertions.assertEquals(
            List.of(true, true, true, true, true, false, new TermCount(1)),
            List.of(
                committed.hasTerm("b"), committed.hasTerm("ba"),
                committed.hasTerm("bb"), committed.hasTerm("bé"),
                committed.hasTerm("c"), committed.hasTerm("a"),
                inverter.frequencies("a", TermFrequencies.NONE).termFrequency()
            ),
            "Prefixed terms and the named term merged, the rest pending"
        );
    }

    @Test
    void flushesLengthsAndEveryTerm() {
        final Inverter inverter = new Inverter();
        inverter.addPosting(DocId.of(1), "apple", new TermCount(2));
        inverter.addPosting(DocId.of(2), "pear", new TermCount(1));
        inverter.lengths().set(DocId.of(1), new TermCount(2), true);
        inverter.lengths().set(DocId.of(2), new TermCount(1), true);
        final MemoryBlocks blocks = InverterTest.blocks();
        final TableWriter table = TableWriter.create(blocks, 0);
        inverter.flush(new PostingWriter(table));
        final PostingTable committed = InverterTest.committed(blocks, table);
        Assertions.assertEquals(
            List.of(
                new TermCount(2), new TermCount(1), true, true, TermCount.ZERO
            ),
            List.of(
                committed.docLength(DocId.of(1)), committed.docLength(DocId.of(2)),
                committed.hasTerm("apple"), committed.hasTerm("pear"),
                inverter.frequencies("apple", TermFrequencies.NONE).termFrequency()
            ),
            "Lengths and postings merged, nothing pending"
        );
    }

    @Test
    void clearsEverything() {
        final Inverter inverter = new Inverter();
        inverter.addPosting(DocId.of(1), "apple", new TermCount(2));
        inverter.lengths().set(DocId.of(1), new TermCount(2), true);
        inverter.positions().set(DocId.of(1), "apple", Positions.list(1, 2));
        inverter.clear();
        Assertions.assertEquals(
            List.of(TermCount.ZERO, false, false),
            List.of(
                inverter.frequencies("apple", TermFrequencies.NONE).termFrequency(),
                inverter.lengths().get(DocId.of(1)).isPresent(),
                inverter.positions().pending()
            ),
            "No change left"
        );
    }

    @Test
    void lengthsReportPendingAndRemovedDocuments() {
        final PendingLengths lengths = new PendingLengths();
        lengths.set(DocId.of(1), new TermCount(5), true);
        lengths.delete(DocId.of(2));
        lengths.set(DocId.of(1), new TermCount(7), false);
        Assertions.assertEquals(
            List.of(new TermCount(7), false),
            List.of(
                lengths.get(DocId.of(1)).orElseThrow(),
                lengths.get(DocId.of(3)).isPresent()
            ),
            "Updated length, unknown document"
        );
        Assertions.assertThrows(
            DocNotFoundError.class, () -> lengths.get(DocId.of(2)),
            "Removed document"
        );
    }

    @Test
    void lengthsRefuseInconsistentChanges() {
        final PendingLengths lengths = new PendingLengths();
        lengths.set(DocId.of(1), new TermCount(5), true);
        lengths.delete(DocId.of(2));
        Assertions.assertThrows(
            InvariantError.class,
            () -> lengths.set(DocId.of(1), new TermCount(5), true),
            "Added twice"
        );
        Assertions.assertThrows(
            InvariantError.class, () -> lengths.delete(DocId.of(2)),
            "Removed twice"
        );
        lengths.delete(DocId.of(1));
        lengths.set(DocId.of(1), new TermCount(1), true);
        lengths.set(DocId.of(2), new TermCount(1), true);
        Assertions.assertEquals(
            new TermCount(1), lengths.get(DocId.of(2)).orElseThrow(),
            "Removed then added again"
        );
    }

    @Test
    void lengthsFlushRemovalsAsZero() {
        final MemoryBlocks blocks = InverterTest.blocks();
        final TableWriter table = TableWriter.create(blocks, 0);
        final PostingWriter writer = new PostingWriter(table);
        final PendingLengths lengths = new PendingLengths();
        lengths.set(DocId.of(1), new TermCount(5), true);
        lengths.set(DocId.of(2), new TermCount(6), true);
        lengths.flush(writer);
        lengths.delete(DocId.of(1));
        lengths.flush(writer);
        final PostingTable committed = InverterTest.committed(blocks, table);
        Assertions.assertEquals(
            List.of(false, true),
            List.of(
                committed.hasDocument(DocId.of(1)), committed.hasDocument(DocId.of(2))
            ),
            "First document removed from the lengths"
        );
    }

    private static MemoryBlocks blocks() {
        return new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
    }

    private static PostingTable committed(
        final MemoryBlocks blocks, final TableWriter table
    ) {
        return PostingTable.of(
            VidrioTable.over(blocks, table.commit(new RevisionNumber(1L)).root())
        );
    }
}
