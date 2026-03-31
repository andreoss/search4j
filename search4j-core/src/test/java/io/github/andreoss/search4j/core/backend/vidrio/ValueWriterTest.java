/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.ValueStats;
import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.InvariantError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link ValueWriter}, {@link ValueChunkWriter} and
 * {@link ValueTable}: chunks, statistics and slots as the reference writes
 * them, chunk edits across revisions, reads before and after a merge.
 *
 * @since 0.1.0
 */
final class ValueWriterTest {

    /**
     * Slot zero.
     */
    private static final ValueNo ZERO = new ValueNo(0L);

    /**
     * Slot one.
     */
    private static final ValueNo ONE = new ValueNo(1L);

    /**
     * Value of a kilobyte.
     */
    private static final String BIG = "k".repeat(1000);

    @Test
    void writesEntriesAsReference() {
        final Values values = Values.of();
        values.writer().add(DocId.of(1), Values.map(0, "hello"));
        values.writer().add(DocId.of(3), Values.map(0, "world"));
        values.writer().add(DocId.of(4), Values.map(0, "x"));
        values.commit();
        final List<String> seen = new ArrayList<>(Values.dump(values.postings()));
        seen.addAll(Values.dump(values.termlists()));
        Assertions.assertEquals(
            List.of(
                "00d0=030568656c6c6f78",
                "00d8000001=0568656c6c6f0105776f726c64000178",
                "000100=00", "000300=00", "000400=00"
            ),
            seen,
            "Statistics, one chunk, the slots used by each document"
        );
    }

    @Test
    void keepsStatsAsReference() {
        final List<Object> seen = new ArrayList<>(14);
        final Values values = Values.of();
        final ValueWriter writer = values.writer();
        seen.add(writer.stats(ValueWriterTest.ZERO));
        writer.add(DocId.of(1), Values.map(0, "hello"));
        seen.add(writer.stats(ValueWriterTest.ONE));
        seen.add(writer.stats(ValueWriterTest.ZERO));
        writer.replace(DocId.of(2), Values.map(0, "world", 1, "cheese"));
        final ValueTable table = values.commit();
        seen.add(table.stats(ValueWriterTest.ZERO));
        seen.add(table.stats(ValueWriterTest.ONE));
        writer.delete(DocId.of(1));
        seen.add(writer.stats(ValueWriterTest.ONE));
        seen.add(writer.stats(ValueWriterTest.ZERO));
        seen.add(table.stats(ValueWriterTest.ZERO));
        writer.delete(DocId.of(2));
        seen.add(writer.stats(ValueWriterTest.ZERO));
        seen.add(writer.stats(ValueWriterTest.ONE));
        writer.replace(DocId.of(2), Values.map(1, "newval"));
        seen.add(writer.stats(ValueWriterTest.ONE));
        seen.add(writer.stats(ValueWriterTest.ZERO));
        seen.add(values.commit().stats(ValueWriterTest.ONE));
        seen.add(Values.dump(values.postings()));
        Assertions.assertEquals(
            List.of(
                ValueStats.NONE, ValueStats.NONE,
                ValueWriterTest.stats(1, "hello", "hello"),
                ValueWriterTest.stats(2, "hello", "world"),
                ValueWriterTest.stats(1, "cheese", "cheese"),
                ValueWriterTest.stats(1, "cheese", "cheese"),
                ValueWriterTest.stats(1, "hello", "world"),
                ValueWriterTest.stats(2, "hello", "world"),
                ValueStats.NONE, ValueStats.NONE,
                ValueWriterTest.stats(1, "newval", "newval"), ValueStats.NONE,
                ValueWriterTest.stats(1, "newval", "newval"),
                List.of("00d001=01066e657776616c", "00d8010002=066e657776616c")
            ),
            seen,
            "Frequency and bounds grow, loosen on deletion, reset at zero"
        );
    }

    @Test
    void splitsChunksAtThreshold() {
        final Values values = Values.of();
        for (int doc = 1; doc <= 3; ++doc) {
            values.writer().add(DocId.of(doc), Values.map(0, ValueWriterTest.BIG));
        }
        final ValueTable table = values.commit();
        final List<Object> seen = new ArrayList<>(3);
        seen.add(values.chunks(0));
        seen.add(
            values.table().get(ValueKeys.chunk(ValueWriterTest.ZERO, DocId.of(1)))
                .orElseThrow().length
        );
        seen.add(ValueWriterTest.read(table.open(ValueWriterTest.ZERO)));
        Assertions.assertEquals(
            List.of(
                List.of(DocId.of(1), DocId.of(3)), 2005,
                List.of("1:k", "2:k", "3:k")
            ),
            seen,
            "A chunk is written once it reaches the threshold"
        );
    }

    @Test
    void splitsChunkReachingThresholdExactly() {
        final Values values = Values.of();
        values.writer().add(DocId.of(1), Values.map(0, "a".repeat(998)));
        values.writer().add(DocId.of(2), Values.map(0, "b".repeat(997)));
        values.writer().add(DocId.of(3), Values.map(0, "c"));
        values.commit();
        Assertions.assertEquals(
            List.of(DocId.of(1), DocId.of(3)), values.chunks(0),
            "A chunk of exactly the threshold is written out"
        );
    }

    @Test
    void editsSingleChunkAcrossRevisions() {
        final List<Object> seen = new ArrayList<>(5);
        final Values values = Values.of();
        values.writer().add(DocId.of(2), Values.map(0, "b"));
        values.writer().add(DocId.of(4), Values.map(0, "d"));
        values.commit();
        seen.add(Values.dump(values.postings()));
        values.writer().add(DocId.of(1), Values.map(0, "a"));
        values.writer().add(DocId.of(3), Values.map(0, "c"));
        values.writer().delete(DocId.of(4));
        values.commit();
        seen.add(Values.dump(values.postings()));
        seen.add(Values.dump(values.termlists()));
        for (int doc = 1; doc <= 3; ++doc) {
            values.writer().delete(DocId.of(doc));
        }
        values.commit();
        seen.add(Values.dump(values.postings()));
        seen.add(Values.dump(values.termlists()));
        Assertions.assertEquals(
            List.of(
                List.of("00d0=02016264", "00d8000002=0162010164"),
                List.of(
                    "00d0=03016164", "00d8000001=0161", "00d8000002=0162000163"
                ),
                List.of("000100=00", "000200=00", "000300=00"),
                List.of(), List.of()
            ),
            seen,
            "A document before the first chunk starts its own, an emptied chunk goes"
        );
    }

    @Test
    void editsSeveralChunks() {
        final List<Object> seen = new ArrayList<>(7);
        final Values values = Values.of();
        for (int doc = 1; doc <= 6; ++doc) {
            values.writer().add(DocId.of(doc), Values.map(0, ValueWriterTest.BIG));
        }
        values.commit();
        seen.add(values.chunks(0));
        values.writer().replace(DocId.of(4), Values.map(0, "d".repeat(1000)));
        values.writer().delete(DocId.of(5));
        values.writer().add(DocId.of(7), Values.map(0, ValueWriterTest.BIG));
        ValueTable table = values.commit();
        seen.add(values.chunks(0));
        seen.add(ValueWriterTest.read(table.open(ValueWriterTest.ZERO)));
        values.writer().delete(DocId.of(3));
        values.writer().delete(DocId.of(4));
        values.writer().add(DocId.of(8), Values.map(0, ValueWriterTest.BIG));
        table = values.commit();
        seen.add(values.chunks(0));
        seen.add(ValueWriterTest.read(table.open(ValueWriterTest.ZERO)));
        seen.add(table.stats(ValueWriterTest.ZERO).frequency());
        values.writer().delete(DocId.of(6));
        values.writer().delete(DocId.of(7));
        values.commit();
        seen.add(values.chunks(0));
        Assertions.assertEquals(
            List.of(
                List.of(DocId.of(1), DocId.of(3), DocId.of(5)),
                List.of(DocId.of(1), DocId.of(3), DocId.of(6)),
                List.of("1:k", "2:k", "3:k", "4:d", "6:k", "7:k"),
                List.of(DocId.of(1), DocId.of(6), DocId.of(8)),
                List.of("1:k", "2:k", "6:k", "7:k", "8:k"),
                new TermCount(5),
                List.of(DocId.of(1), DocId.of(8))
            ),
            seen,
            "Change within a chunk, a later chunk renamed, one dropped, one appended"
        );
    }

    @Test
    void readsChangesBeforeTables() {
        final List<Object> seen = new ArrayList<>(12);
        final Values values = Values.of();
        final ValueWriter writer = values.writer();
        seen.add(writer.modified());
        writer.add(DocId.of(1), Values.map(0, "a", 5, "f"));
        seen.add(writer.modified());
        seen.add(writer.value(DocId.of(1), ValueWriterTest.ZERO));
        seen.add(writer.values(DocId.of(1)));
        seen.add(writer.values(DocId.of(2)));
        final ValueTable table = values.commit();
        seen.add(writer.modified());
        writer.replace(DocId.of(1), Values.map(0, "b"));
        seen.add(writer.value(DocId.of(1), ValueWriterTest.ZERO));
        seen.add(writer.values(DocId.of(1)));
        seen.add(table.value(DocId.of(1), ValueWriterTest.ZERO));
        writer.delete(DocId.of(1));
        seen.add(writer.value(DocId.of(1), ValueWriterTest.ZERO));
        seen.add(writer.values(DocId.of(1)));
        writer.cancel();
        seen.add(writer.modified());
        seen.add(writer.value(DocId.of(1), new ValueNo(5L)));
        seen.add(writer.stats(ValueWriterTest.ZERO));
        Assertions.assertEquals(
            List.of(
                false, true, "a", Values.map(0, "a", 5, "f"), new TreeMap<>(),
                false, "b", Values.map(0, "b"), "a", "", new TreeMap<>(),
                false, "f", ValueWriterTest.stats(1, "a", "a")
            ),
            seen,
            "Pending changes shadow the tables until cancelled"
        );
    }

    @Test
    void readsAbsentValuesAsEmpty() {
        final List<Object> seen = new ArrayList<>(6);
        final Values values = Values.of();
        values.writer().add(DocId.of(5), Values.map(1, "e"));
        values.writer().add(DocId.of(7), Values.map(1, "g"));
        values.writer().add(DocId.of(9), new TreeMap<>());
        final ValueTable table = values.commit();
        seen.add(table.value(DocId.of(5), ValueWriterTest.ZERO));
        seen.add(table.value(DocId.of(3), ValueWriterTest.ONE));
        seen.add(table.value(DocId.of(6), ValueWriterTest.ONE));
        seen.add(table.value(DocId.of(8), ValueWriterTest.ONE));
        seen.add(table.values(DocId.of(9)));
        seen.add(Values.dump(values.termlists()));
        Assertions.assertEquals(
            List.of(
                "", "", "", "", new TreeMap<>(), List.of("000500=01", "000700=01")
            ),
            seen,
            "No chunk, before the first chunk, a gap, past the end, no slots"
        );
    }

    @Test
    void forgetsSlotsOfDocumentDeletedThenAddedEmpty() {
        final Values values = Values.of();
        values.writer().add(DocId.of(1), Values.map(0, "a"));
        values.commit();
        values.writer().delete(DocId.of(1));
        values.writer().add(DocId.of(1), new TreeMap<>());
        values.commit();
        Assertions.assertEquals(
            List.of(), Values.dump(values.termlists()),
            "Slots entry removed when the document is added back without values"
        );
    }

    @Test
    void refusesEmptyValue() {
        final Values values = Values.of();
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> values.writer().add(DocId.of(1), Values.map(0, "")),
            "Empty values are not stored"
        );
    }

    @Test
    void refusesDeletionWithoutStats() {
        final Values values = Values.of();
        values.termlists().add(ValueKeys.slots(DocId.of(1)), new byte[]{0});
        Assertions.assertEquals(
            "Value frequency of slot 0 below zero",
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> values.writer().delete(DocId.of(1)),
                "Slots used without statistics"
            ).getMessage(),
            "The frequency cannot drop below zero"
        );
    }

    @Test
    void refusesValuesOutOfOrder() {
        final ValueChunkWriter writer =
            ValueChunkWriter.over(Values.of().postings(), ValueWriterTest.ZERO);
        writer.update(DocId.of(5), "e");
        Assertions.assertThrows(
            InvariantError.class,
            () -> writer.update(DocId.of(3), "c"),
            "Changes must come in document order"
        );
    }

    static List<String> read(final VidrioValueList list) {
        final List<String> result = new ArrayList<>(0);
        list.next();
        while (!list.atEnd()) {
            result.add(
                String.format(
                    "%s:%s", list.docId(), list.value().substring(0, 1)
                )
            );
            list.next();
        }
        return result;
    }

    private static ValueStats stats(
        final int freq, final String lower, final String upper
    ) {
        return new ValueStats(new TermCount(freq), lower, upper);
    }
}
