/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.ser.Varints;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link ByteReader}.
 *
 * @since 0.1.0
 */
final class ByteReaderTest {

    @Test
    void readsPackedIntegers() {
        final ByteReader reader = ByteReader.over(
            ByteReaderTest.bytes("00ff01ffffffff0fffffffffffffffffff01")
        );
        Assertions.assertEquals(
            List.of(0L, 255L, 0xFFFF_FFFFL, -1L, true),
            List.of(
                reader.narrow("a"), reader.narrow("b"), reader.narrow("c"),
                reader.wide("d"), reader.done()
            ),
            "One, two, five and ten byte values"
        );
    }

    @Test
    void readsRoundTripOfPackedValues() {
        final long[] values = {
            0L, 1L, 127L, 128L, 16_383L, 16_384L, 0xFFFF_FFFFL,
            Long.MAX_VALUE,
        };
        final byte[][] packed = new byte[values.length][];
        int total = 0;
        for (int idx = 0; idx < values.length; ++idx) {
            packed[idx] = Varints.pack(values[idx]);
            total += packed[idx].length;
        }
        final byte[] data = new byte[total];
        int offset = 0;
        for (final byte[] item : packed) {
            System.arraycopy(item, 0, data, offset, item.length);
            offset += item.length;
        }
        final ByteReader reader = ByteReader.over(data);
        final long[] read = new long[values.length];
        for (int idx = 0; idx < values.length; ++idx) {
            read[idx] = reader.wide("value");
        }
        Assertions.assertArrayEquals(values, read, "Round trip");
    }

    @Test
    void readsRangeOnly() {
        final ByteReader reader = ByteReader.over(
            ByteReaderTest.bytes("0102030405"), 1, 3
        );
        Assertions.assertEquals(
            List.of(2L, false, "03", true),
            List.of(
                reader.wide("a"), reader.done(),
                HexFormat.of().formatHex(reader.rest()), reader.done()
            ),
            "Bytes 1 to 3"
        );
    }

    @Test
    void readsStrings() {
        final ByteReader reader = ByteReader.over(
            ByteReaderTest.bytes("03616263000161")
        );
        Assertions.assertEquals(
            List.of("616263", "", "61"),
            List.of(
                HexFormat.of().formatHex(reader.string("a")),
                HexFormat.of().formatHex(reader.string("b")),
                HexFormat.of().formatHex(reader.string("c"))
            ),
            "Length-prefixed strings"
        );
    }

    @Test
    void rejectsIntegerOutOfData() {
        final ByteReader reader = ByteReader.over(ByteReaderTest.bytes("80"));
        Assertions.assertEquals(
            "Count out of data",
            Assertions.assertThrows(
                DatabaseCorruptError.class, () -> reader.wide("Count"),
                "Continuation byte at the end"
            ).getMessage(),
            "Message"
        );
    }

    @Test
    void rejectsEmptyInput() {
        final ByteReader reader = ByteReader.over(new byte[0]);
        Assertions.assertThrows(
            DatabaseCorruptError.class, () -> reader.narrow("Count"),
            "Nothing to read"
        );
    }

    @Test
    void rejectsStringOutOfData() {
        final ByteReader reader = ByteReader.over(ByteReaderTest.bytes("0361"));
        Assertions.assertThrows(
            DatabaseCorruptError.class, () -> reader.string("Name"),
            "Length past the end"
        );
    }

    @Test
    void rejectsStringLengthOverflow() {
        final ByteReader reader = ByteReader.over(
            ByteReaderTest.bytes("ffffffffffffffffff0161")
        );
        Assertions.assertThrows(
            DatabaseCorruptError.class, () -> reader.string("Name"),
            "Length above the long range"
        );
    }

    @Test
    void rejectsNarrowOverflowByValue() {
        final ByteReader reader = ByteReader.over(
            ByteReaderTest.bytes("ffffffff1f")
        );
        Assertions.assertEquals(
            "Count overflowed",
            Assertions.assertThrows(
                DatabaseCorruptError.class, () -> reader.narrow("Count"),
                "33 bits"
            ).getMessage(),
            "Message"
        );
    }

    @Test
    void rejectsNarrowOverflowByLength() {
        final ByteReader reader = ByteReader.over(
            ByteReaderTest.bytes("808080808000")
        );
        Assertions.assertThrows(
            DatabaseCorruptError.class, () -> reader.narrow("Count"),
            "Six bytes, as the reference rejects"
        );
    }

    @Test
    void acceptsNarrowAtFiveBytes() {
        Assertions.assertEquals(
            0L,
            ByteReader.over(ByteReaderTest.bytes("8080808000")).narrow("Count"),
            "Five bytes"
        );
    }

    @Test
    void rejectsWideOverflowByValue() {
        final ByteReader reader = ByteReader.over(
            ByteReaderTest.bytes("ffffffffffffffffff02")
        );
        Assertions.assertThrows(
            DatabaseCorruptError.class, () -> reader.wide("Count"),
            "65 bits"
        );
    }

    @Test
    void rejectsWideOverflowByLength() {
        final ByteReader reader = ByteReader.over(
            ByteReaderTest.bytes("8080808080808080808000")
        );
        Assertions.assertThrows(
            DatabaseCorruptError.class, () -> reader.wide("Count"),
            "Eleven bytes"
        );
    }

    @Test
    void readsWideAtTenBytes() {
        Assertions.assertEquals(
            Long.MIN_VALUE,
            ByteReader.over(ByteReaderTest.bytes("80808080808080808001"))
                .wide("Count"),
            "Bit 63 set"
        );
    }

    @Test
    void leavesPositionOnFailure() {
        final ByteReader reader = ByteReader.over(
            ByteReaderTest.bytes("ffffffff1f05")
        );
        Assertions.assertThrows(
            DatabaseCorruptError.class, () -> reader.narrow("Count"),
            "Overflow"
        );
        Assertions.assertEquals(
            0x1_FFFF_FFFFL, reader.wide("Count"),
            "Same value read as wide"
        );
    }

    private static byte[] bytes(final String hex) {
        return HexFormat.of().parseHex(hex);
    }
}
