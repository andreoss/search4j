/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the serialisation utilities.
 */
package io.github.andreoss.search4j.core.ser;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link SortableStrings}.
 *
 * @since 0.1.0
 */
final class SortableStringsTest {

    @Test
    void packsWithAndWithoutTerminator() {
        Assertions.assertEquals(
            List.of("616263", "61626300", "6100ff62", "6100ff6200", "", "00"),
            List.of(
                SortableStringsTest.hex("abc", true),
                SortableStringsTest.hex("abc", false),
                SortableStringsTest.hex("a\0b", true),
                SortableStringsTest.hex("a\0b", false),
                SortableStringsTest.hex("", true),
                SortableStringsTest.hex("", false)
            ),
            "Zero bytes escaped, terminator unless last"
        );
    }

    @Test
    void unpacksUpToTerminatorAndLeavesRest() {
        final ByteBuffer buf = ByteBuffer.wrap(
            HexFormat.of().parseHex("6100ff620063")
        );
        Assertions.assertEquals(
            List.of("610062", 5, "63", 6, "", 6),
            List.of(
                SortableStringsTest.hex(SortableStrings.unpack(buf)),
                buf.position(),
                SortableStringsTest.hex(SortableStrings.unpack(buf)),
                buf.position(),
                SortableStringsTest.hex(SortableStrings.unpack(buf)),
                buf.position()
            ),
            "Escaped zero kept, terminator consumed, end tolerated"
        );
    }

    @Test
    void unpacksZeroAtTheEndAsTerminator() {
        final ByteBuffer buf = ByteBuffer.wrap(new byte[] {0x61, 0x00});
        Assertions.assertEquals(
            List.of("61", 2),
            List.of(
                SortableStringsTest.hex(SortableStrings.unpack(buf)),
                buf.position()
            ),
            "Zero as the last byte ends the string"
        );
    }

    @Test
    void roundTripsRandomStrings() {
        final Random random = new Random(11L);
        final List<String> faults = new ArrayList<>(0);
        for (int idx = 0; idx < 500; ++idx) {
            final byte[] value = new byte[random.nextInt(20)];
            for (int pos = 0; pos < value.length; ++pos) {
                value[pos] = (byte) random.nextInt(3);
            }
            final byte[] packed = SortableStrings.pack(value, false);
            final byte[] back =
                SortableStrings.unpack(ByteBuffer.wrap(packed));
            if (!Arrays.equals(value, back)) {
                faults.add(SortableStringsTest.hex(value));
            }
        }
        Assertions.assertEquals(List.of(), faults, "Every string read back");
    }

    @Test
    void keepsByteOrder() {
        final Random random = new Random(12L);
        final List<byte[]> values = new ArrayList<>(300);
        for (int idx = 0; idx < 300; ++idx) {
            final byte[] value = new byte[random.nextInt(6)];
            for (int pos = 0; pos < value.length; ++pos) {
                value[pos] = (byte) (random.nextInt(3) - 1);
            }
            values.add(value);
        }
        values.sort(Arrays::compareUnsigned);
        final List<String> faults = new ArrayList<>(0);
        for (int idx = 1; idx < values.size(); ++idx) {
            final byte[] prev = SortableStrings.pack(values.get(idx - 1), false);
            final byte[] next = SortableStrings.pack(values.get(idx), false);
            if (Arrays.compareUnsigned(prev, next) > 0) {
                faults.add(SortableStringsTest.hex(values.get(idx)));
            }
        }
        Assertions.assertEquals(
            List.of(), faults, "Packed order follows string order"
        );
    }

    private static String hex(final String text, final boolean last) {
        return SortableStringsTest.hex(
            SortableStrings.pack(
                text.getBytes(StandardCharsets.US_ASCII), last
            )
        );
    }

    private static String hex(final byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
