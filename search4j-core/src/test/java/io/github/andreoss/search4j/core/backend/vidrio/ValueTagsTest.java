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
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link ValueChunk}, {@link ValueStatsTag} and {@link SlotsTag}:
 * vectors derived from the reference encoder, and every damage.
 *
 * @since 0.1.0
 */
final class ValueTagsTest {

    /**
     * Chunk of the values hello, world and x in documents 1, 3 and 4.
     */
    private static final String CHUNK = "0568656c6c6f0105776f726c64000178";

    @Test
    void readsChunkAsReference() {
        final List<String> seen = new ArrayList<>(5);
        final ValueChunk chunk = ValueTagsTest.chunk(ValueTagsTest.CHUNK);
        seen.add(ValueTagsTest.at(chunk));
        chunk.next();
        seen.add(ValueTagsTest.at(chunk));
        chunk.next();
        seen.add(ValueTagsTest.at(chunk));
        chunk.next();
        seen.add(ValueTagsTest.at(chunk));
        chunk.next();
        seen.add(ValueTagsTest.at(chunk));
        Assertions.assertEquals(
            List.of("1:hello", "3:world", "4:x", "end", "end"),
            seen,
            "First value, then distances one below and values"
        );
    }

    @Test
    void skipsWithinChunk() {
        final List<String> seen = new ArrayList<>(6);
        final ValueChunk chunk = ValueTagsTest.chunk(ValueTagsTest.CHUNK);
        chunk.skipTo(DocId.of(1));
        seen.add(ValueTagsTest.at(chunk));
        chunk.skipTo(DocId.of(2));
        seen.add(ValueTagsTest.at(chunk));
        chunk.skipTo(DocId.of(1));
        seen.add(ValueTagsTest.at(chunk));
        chunk.skipTo(DocId.of(4));
        seen.add(ValueTagsTest.at(chunk));
        chunk.skipTo(DocId.of(9));
        seen.add(ValueTagsTest.at(chunk));
        final ValueChunk none = ValueChunk.none();
        none.skipTo(DocId.of(1));
        seen.add(ValueTagsTest.at(none));
        Assertions.assertEquals(
            List.of("1:hello", "3:world", "3:world", "4:x", "end", "end"),
            seen,
            "Skip to a document, past a gap, never backwards, to the end"
        );
    }

    @Test
    void refusesDamagedChunks() {
        final List<String> seen = new ArrayList<>(7);
        for (final String tag
            : List.of("", "0568", "016180", "016101", "0161000561", "0161ffffffff0f0161")) {
            seen.add(
                Assertions.assertThrows(
                    DatabaseCorruptError.class,
                    () -> ValueTagsTest.drain(ValueTagsTest.chunk(tag)),
                    tag
                ).getMessage()
            );
        }
        seen.add(
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> ValueTagsTest.chunk("0161ffffffff0f0161").skipTo(DocId.of(5)),
                "Skip over an overflowing distance"
            ).getMessage()
        );
        Assertions.assertEquals(
            List.of(
                "First value out of data", "First value out of data",
                "Streamed value document out of data",
                "Streamed value out of data", "Streamed value out of data",
                "Document number out of range in value chunk",
                "Document number out of range in value chunk"
            ),
            seen,
            "Empty, truncated first value, distance, length, value, overflow"
        );
    }

    @Test
    void codesStatsAsReference() {
        final List<Object> seen = new ArrayList<>(6);
        seen.add(ValueTagsTest.stats("0601686e"));
        seen.add(ValueTagsTest.stats("010568656c6c6f"));
        seen.add(ValueTagsTest.stats("0100"));
        seen.add(ValueTagsTest.stats("0000"));
        seen.add(
            HexFormat.of().formatHex(
                ValueStatsTag.encode(new ValueStats(new TermCount(3), "hello", "x"))
            )
        );
        seen.add(
            HexFormat.of().formatHex(
                ValueStatsTag.encode(new ValueStats(new TermCount(1), "hello", "hello"))
            )
        );
        Assertions.assertEquals(
            List.of(
                new ValueStats(new TermCount(6), "h", "n"),
                new ValueStats(new TermCount(1), "hello", "hello"),
                new ValueStats(
                    new TermCount(1), Character.toString(0), Character.toString(0)
                ),
                ValueStats.NONE, "030568656c6c6f78", "010568656c6c6f"
            ),
            seen,
            "Frequency, lower bound, upper bound omitted when equal"
        );
    }

    @Test
    void refusesDamagedStats() {
        final List<String> seen = new ArrayList<>(5);
        for (final String tag : List.of("", "06", "0603", "ffffffff0f00", "ffffffffff0f00")) {
            seen.add(
                Assertions.assertThrows(
                    DatabaseCorruptError.class,
                    () -> ValueTagsTest.stats(tag),
                    tag
                ).getMessage()
            );
        }
        Assertions.assertEquals(
            List.of(
                "Value frequency out of data", "Value lower bound out of data",
                "Value lower bound out of data", "Value frequency out of range",
                "Value frequency overflowed"
            ),
            seen,
            "Empty, missing bound, truncated bound, frequency out of range"
        );
    }

    @Test
    void codesSlotsAsReference() {
        final List<Object> seen = new ArrayList<>(6);
        final List<ValueNo> powers = new ArrayList<>(24);
        for (int idx = 0; idx < 24; ++idx) {
            powers.add(new ValueNo(1L << idx));
        }
        seen.add(HexFormat.of().formatHex(SlotsTag.encode(List.of())));
        seen.add(HexFormat.of().formatHex(SlotsTag.encode(List.of(new ValueNo(0L)))));
        seen.add(
            HexFormat.of().formatHex(
                SlotsTag.encode(
                    List.of(new ValueNo(0L), new ValueNo(1L), new ValueNo(3L))
                )
            )
        );
        seen.add(HexFormat.of().formatHex(SlotsTag.encode(powers)));
        seen.add(SlotsTag.decode(SlotsTag.encode(powers)));
        seen.add(SlotsTag.decode(HexFormat.of().parseHex("ffffffff0f")));
        Assertions.assertEquals(
            List.of(
                "", "00", "000001",
                "01000103070f1f3f7fff01ff03ff07ff0fff1fff3fff7fffff01ffff03ffff07ffff0fffff1fffff3fffff7fffffff01",
                powers, List.of(new ValueNo(ValueNo.BAD))
            ),
            seen,
            "Each slot as its distance one below the previous, the first as is"
        );
    }

    @Test
    void refusesDamagedSlots() {
        final List<String> seen = new ArrayList<>(2);
        for (final String tag : List.of("ff", "ffffffff0f00")) {
            seen.add(
                Assertions.assertThrows(
                    DatabaseCorruptError.class,
                    () -> SlotsTag.decode(HexFormat.of().parseHex(tag)),
                    tag
                ).getMessage()
            );
        }
        Assertions.assertEquals(
            List.of("Value slot out of data", "Value slot encoding corrupt"),
            seen,
            "Truncated distance, slot past the last"
        );
    }

    private static ValueChunk chunk(final String hex) {
        return ValueChunk.of(HexFormat.of().parseHex(hex), DocId.of(1));
    }

    private static ValueStats stats(final String hex) {
        return ValueStatsTag.decode(HexFormat.of().parseHex(hex));
    }

    private static String at(final ValueChunk chunk) {
        final String result;
        if (chunk.atEnd()) {
            result = "end";
        } else {
            result = String.format("%s:%s", chunk.docId(), chunk.value());
        }
        return result;
    }

    private static void drain(final ValueChunk chunk) {
        while (!chunk.atEnd()) {
            chunk.next();
        }
    }
}
