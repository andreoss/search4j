/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link BranchItem}.
 *
 * @since 0.1.0
 */
final class BranchItemTest {

    @Test
    void encodesBlockKeyAndCount() {
        final BranchItem item = new BranchItem(
            ItemKey.of(new byte[] {0x61}), 3, new BlockNumber(0x01020304L)
        );
        Assertions.assertEquals(
            List.of(8, "0102030401610003"),
            List.of(item.size(), HexFormat.of().formatHex(item.bytes())),
            "B K1 key X2"
        );
    }

    @Test
    void encodesNullKeyInSevenBytes() {
        Assertions.assertEquals(
            "ffffffff000000",
            HexFormat.of().formatHex(
                BranchItem.nullKey(BlockNumber.fromBits(-1)).bytes()
            ),
            "Null key item"
        );
    }

    @Test
    void roundTripsThroughBlock() {
        final BranchItem item = new BranchItem(
            ItemKey.of(new byte[] {1, 2, 3}), 0xFFFF, new BlockNumber(9L)
        );
        final byte[] block = new byte[item.size() + 2];
        System.arraycopy(item.bytes(), 0, block, 2, item.size());
        Assertions.assertEquals(
            item, BranchItem.parse(block, 2), "Same item"
        );
    }

    @Test
    void parsesItemFillingBlock() {
        Assertions.assertEquals(
            BranchItem.nullKey(new BlockNumber(1L)),
            BranchItem.parse(HexFormat.of().parseHex("00000001000000"), 0),
            "Seven bytes at offset zero"
        );
    }

    @Test
    void rejectsBadComponents() {
        for (final int component : new int[] {-1, 0x10000}) {
            Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new BranchItem(
                    ItemKey.EMPTY, component, new BlockNumber(0L)
                ),
                String.format("Component %d", component)
            );
        }
    }

    @Test
    void rejectsCorruptItems() {
        final String[][] cases = {
            {"000000000000", "0"},
            {"00000000000000", "1"},
            {"00000000000000", "-1"},
            {"000000000161", "0"},
            {"0000000001610000", "1"},
            {"00000000016100", "0"},
        };
        for (final String[] item : cases) {
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> BranchItem.parse(
                    HexFormat.of().parseHex(item[0]), Integer.parseInt(item[1])
                ),
                item[0]
            );
        }
    }

    @Test
    void ordersByKeyThenComponent() {
        final BranchItem low = BranchItem.nullKey(new BlockNumber(5L));
        final BranchItem high = new BranchItem(
            ItemKey.EMPTY, 1, new BlockNumber(1L)
        );
        Assertions.assertEquals(
            List.of(-1, 1, -1),
            List.of(
                low.order(high), high.order(low), low.order(LeafItem.root())
            ),
            "Item order"
        );
    }
}
