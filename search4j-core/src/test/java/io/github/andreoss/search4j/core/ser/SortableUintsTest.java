/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the serialisation utilities.
 */
package io.github.andreoss.search4j.core.ser;

import io.github.andreoss.search4j.core.error.SerialisationError;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link SortableUints}.
 *
 * @since 0.1.0
 */
final class SortableUintsTest {

    /**
     * Values at band boundaries and their encodings, in hexadecimal.
     */
    private static final String[][] BANDS = {
        {"0", "0000"},
        {"7fff", "7fff"},
        {"8000", "808000"},
        {"3fffff", "bfffff"},
        {"400000", "c0400000"},
        {"1fffffff", "dfffffff"},
        {"20000000", "e020000000"},
        {"ffffffffffffff", "fcffffffffffffff"},
        {"100000000000000", "fd00000000000000"},
        {"1ffffffffffffff", "fdffffffffffffff"},
        {"200000000000000", "fe0200000000000000"},
        {"ffffffffffffffff", "feffffffffffffffff"},
    };

    @Test
    void roundTripsSmallValuesInOrder() {
        byte[] prev = new byte[0];
        for (long value = 0L; value != 1000L; ++value) {
            prev = SortableUintsTest.check(value, prev);
        }
        for (long value = 2345L; value < 65_000L; value += 113L) {
            prev = SortableUintsTest.check(value, prev);
        }
    }

    @Test
    void roundTripsGrowingValuesInOrder() {
        byte[] prev = SortableUints.pack(64_999L);
        long last = 64_999L;
        for (long value = 65_000L; value > last && value <= 0xFFFF_FFFFL;
            value = value << 1 ^ 1337L) {
            prev = SortableUintsTest.check(value, prev);
            last = value;
        }
        Assertions.assertTrue(last > 0x8000_0000L, "Reached 32 bits");
    }

    @Test
    void decodesConcatenatedValues() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (long value = 23_456L; value < 765_432L; value += 1131L) {
            out.writeBytes(SortableUints.pack(value));
        }
        final List<Long> expected = new ArrayList<>(0);
        for (long value = 23_456L; value < 765_432L; value += 1131L) {
            expected.add(value);
        }
        final ByteBuffer buf = ByteBuffer.wrap(out.toByteArray());
        final List<Long> actual = new ArrayList<>(expected.size());
        while (buf.hasRemaining()) {
            actual.add(SortableUints.unpack(buf));
        }
        Assertions.assertEquals(expected, actual, "Sequential values");
    }

    /**
     * Values at band boundaries and their encodings, in hexadecimal.
     */
    @Test
    void encodesBandBoundaries() {
        for (final String[] item : SortableUintsTest.BANDS) {
            Assertions.assertEquals(
                item[1],
                HexFormat.of().formatHex(
                    SortableUints.pack(Long.parseUnsignedLong(item[0], 16))
                ),
                item[0]
            );
        }
    }

    @Test
    void decodesBandBoundaries() {
        for (final String[] item : SortableUintsTest.BANDS) {
            Assertions.assertEquals(
                Long.parseUnsignedLong(item[0], 16),
                SortableUints.unpack(
                    ByteBuffer.wrap(HexFormat.of().parseHex(item[1]))
                ),
                item[0]
            );
        }
    }

    @Test
    void reportsWidths() {
        Assertions.assertEquals(
            List.of(2, 2, 3, 4, 5, 8, 9, 0),
            List.of(
                SortableUints.width((byte) 0x00),
                SortableUints.width((byte) 0x7F),
                SortableUints.width((byte) 0x80),
                SortableUints.width((byte) 0xC0),
                SortableUints.width((byte) 0xE0),
                SortableUints.width((byte) 0xFC),
                SortableUints.width((byte) 0xFE),
                SortableUints.width((byte) 0xFF)
            ),
            "Width from lead byte"
        );
    }

    @Test
    void ordersFullRangeUnsigned() {
        Assertions.assertTrue(
            Arrays.compareUnsigned(
                SortableUints.pack(Long.MAX_VALUE), SortableUints.pack(-1L)
            ) < 0,
            "Unsigned order"
        );
    }

    @Test
    void rejectsEmptyInput() {
        Assertions.assertThrows(
            SerialisationError.class,
            () -> SortableUints.unpack(ByteBuffer.allocate(0)),
            "Empty"
        );
    }

    @Test
    void rejectsBadLeadByte() {
        Assertions.assertThrows(
            SerialisationError.class,
            () -> SortableUints.unpack(
                ByteBuffer.wrap(new byte[] {(byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0})
            ),
            "Lead 0xff"
        );
    }

    @Test
    void rejectsTruncatedShortForm() {
        Assertions.assertThrows(
            SerialisationError.class,
            () -> SortableUints.unpack(ByteBuffer.wrap(new byte[] {0x12})),
            "One byte"
        );
    }

    @Test
    void rejectsTruncatedLongForm() {
        Assertions.assertThrows(
            SerialisationError.class,
            () -> SortableUints.unpack(
                ByteBuffer.wrap(new byte[] {(byte) 0xC0, 0x40, 0x00})
            ),
            "Missing byte"
        );
    }

    @Test
    void decodesExactLengthLongForm() {
        Assertions.assertEquals(
            0x400000L,
            SortableUints.unpack(
                ByteBuffer.wrap(new byte[] {(byte) 0xC0, 0x40, 0x00, 0x00})
            ),
            "Four bytes"
        );
    }

    private static byte[] check(final long value, final byte[] prev) {
        final byte[] packed = SortableUints.pack(value);
        final ByteBuffer buf = ByteBuffer.wrap(packed);
        Assertions.assertEquals(value, SortableUints.unpack(buf), "Value");
        Assertions.assertFalse(buf.hasRemaining(), "Consumed");
        Assertions.assertTrue(
            Arrays.compareUnsigned(prev, packed) < 0,
            String.format("Order at %d", value)
        );
        return packed;
    }
}
