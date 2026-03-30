/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link ItemKey}.
 *
 * @since 0.1.0
 */
final class ItemKeyTest {

    @Test
    void encodesLengthPrefix() {
        Assertions.assertEquals(
            "03616263",
            HexFormat.of().formatHex(ItemKeyTest.key("616263").bytes()),
            "K1 then key"
        );
    }

    @Test
    void encodesEmptyKeyAsOneByte() {
        Assertions.assertArrayEquals(
            new byte[] {0}, ItemKey.EMPTY.bytes(), "Empty key"
        );
    }

    @Test
    void acceptsLongestKey() {
        final ItemKey key = ItemKey.of(new byte[ItemKey.MAX_LENGTH]);
        Assertions.assertEquals(
            List.of(255, 256, (byte) 0xFF),
            List.of(key.length(), key.bytes().length, key.bytes()[0]),
            "255 bytes"
        );
    }

    @Test
    void rejectsTooLongKey() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> ItemKey.of(new byte[256]),
            "256 bytes"
        );
    }

    @Test
    void ordersUnsignedThenByLength() {
        Assertions.assertEquals(
            List.of(-1, 1, 0, -1),
            List.of(
                ItemKeyTest.order("7f", "80"),
                ItemKeyTest.order("6162", "61"),
                ItemKeyTest.order("61", "61"),
                Integer.signum(ItemKey.EMPTY.compareTo(ItemKeyTest.key("00")))
            ),
            "Unsigned bytes, prefix first"
        );
    }

    @Test
    void parsesAtOffset() {
        Assertions.assertEquals(
            ItemKeyTest.key("6263"),
            ItemKey.parse(HexFormat.of().parseHex("ff026263ff"), 1),
            "Key at offset 1"
        );
    }

    @Test
    void parsesKeyEndingAtBlockEnd() {
        Assertions.assertEquals(
            ItemKeyTest.key("62"),
            ItemKey.parse(HexFormat.of().parseHex("0162"), 0),
            "Key at end"
        );
    }

    @Test
    void rejectsKeyPastBlock() {
        for (final int offset : new int[] {-1, 2}) {
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> ItemKey.parse(new byte[] {0, 0}, offset),
                String.format("Offset %d", offset)
            );
        }
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> ItemKey.parse(HexFormat.of().parseHex("0262"), 0),
            "Overrun"
        );
    }

    @Test
    void copiesBytes() {
        final byte[] raw = {1, 2};
        final ItemKey key = ItemKey.of(raw);
        raw[0] = 9;
        key.data()[1] = 9;
        Assertions.assertEquals("0102", key.toString(), "Defensive copies");
    }

    @Test
    void comparesByContent() {
        Assertions.assertEquals(
            List.of(true, false),
            List.of(
                ItemKeyTest.key("01").equals(ItemKeyTest.key("01")),
                ItemKeyTest.key("01").equals(ItemKeyTest.key("02"))
            ),
            "Value equality"
        );
    }

    @Test
    void differsFromOtherTypes() {
        Assertions.assertNotEquals(ItemKeyTest.key("01"), new Object(), "Type");
    }

    @Test
    void hashesDistinctContent() {
        Assertions.assertNotEquals(
            ItemKeyTest.key("01").hashCode(), ItemKeyTest.key("02").hashCode(),
            "Distinct hashes"
        );
    }

    @Test
    void hashesByContent() {
        Assertions.assertEquals(
            ItemKeyTest.key("0102").hashCode(), ItemKeyTest.key("0102").hashCode(), "Hash"
        );
    }

    private static int order(final String left, final String right) {
        return Integer.signum(
            ItemKeyTest.key(left).compareTo(ItemKeyTest.key(right))
        );
    }

    private static ItemKey key(final String hex) {
        return ItemKey.of(HexFormat.of().parseHex(hex));
    }
}
