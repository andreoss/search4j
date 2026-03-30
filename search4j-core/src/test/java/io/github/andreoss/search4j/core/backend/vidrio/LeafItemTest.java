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
 * Tests of {@link LeafItem} and {@link TagPart}.
 *
 * @since 0.1.0
 */
final class LeafItemTest {

    /**
     * Key "ab".
     */
    private static final ItemKey KEY = ItemKey.of(new byte[] {0x61, 0x62});

    @Test
    void encodesRootItemInThreeBytes() {
        Assertions.assertEquals(
            "600000", LeafItemTest.hex(LeafItem.root()), "Root item"
        );
    }

    @Test
    void encodesFirstComponentWithoutCount() {
        final LeafItem item = LeafItem.of(
            LeafItemTest.KEY, TagPart.WHOLE, new byte[] {7, 8, 9}
        );
        Assertions.assertEquals(
            List.of(8, "6005026162070809"),
            List.of(item.size(), LeafItemTest.hex(item)),
            "I2 K1 key tag"
        );
    }

    @Test
    void encodesLaterComponentWithCount() {
        final LeafItem item = LeafItem.of(
            LeafItemTest.KEY, new TagPart(2, true, false), new byte[] {7}
        );
        Assertions.assertEquals(
            List.of(8, "8005026162000207"),
            List.of(item.size(), LeafItemTest.hex(item)),
            "I2 K1 key X2 tag"
        );
    }

    @Test
    void mapsPartToFlags() {
        Assertions.assertEquals(
            List.of(0x60, 0xA0, 0x00, 0xC0, 0x20),
            List.of(
                TagPart.WHOLE.flags(), new TagPart(1, true, false).flags(),
                new TagPart(2, false, false).flags(),
                new TagPart(3, true, true).flags(),
                new TagPart(1, false, false).flags()
            ),
            "High size byte"
        );
    }

    @Test
    void roundTripsThroughBlock() {
        final LeafItem item = LeafItem.of(
            LeafItemTest.KEY, new TagPart(513, true, true), new byte[] {1, 2}
        );
        final byte[] block = new byte[20];
        System.arraycopy(item.bytes(), 0, block, 5, item.size());
        final LeafItem back = LeafItem.parse(block, 5);
        Assertions.assertEquals(
            List.of(item, LeafItemTest.KEY, 513, "0102"),
            List.of(
                back, back.key(), back.component(),
                HexFormat.of().formatHex(back.chunk())
            ),
            "Same item"
        );
    }

    @Test
    void parsesFlags() {
        Assertions.assertEquals(
            List.of(
                new TagPart(1, true, false), new TagPart(2, false, true)
            ),
            List.of(
                LeafItem.parse(HexFormat.of().parseHex("a00000"), 0).part(),
                LeafItem.parse(HexFormat.of().parseHex("4002000002"), 0).part()
            ),
            "Flags from the high size byte"
        );
    }

    @Test
    void parsesItemEndingAtBlockEnd() {
        Assertions.assertEquals(
            LeafItem.root(),
            LeafItem.parse(HexFormat.of().parseHex("00600000"), 1),
            "Root at end"
        );
    }

    @Test
    void fitsLargestItem() {
        final LeafItem item = LeafItem.of(
            ItemKey.EMPTY, new TagPart(1, false, false),
            new byte[LeafItem.MAX_SIZE - 3]
        );
        Assertions.assertEquals(
            List.of(8194, "3fff"),
            List.of(item.size(), HexFormat.of().formatHex(item.bytes(), 0, 2)),
            "Largest size field"
        );
    }

    @Test
    void rejectsTooLargeItem() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> LeafItem.of(
                ItemKey.EMPTY, TagPart.WHOLE, new byte[LeafItem.MAX_SIZE - 2]
            ),
            "One byte too many"
        );
    }

    @Test
    void countsComponentBytesInSize() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> LeafItem.of(
                ItemKey.EMPTY, new TagPart(2, false, false),
                new byte[LeafItem.MAX_SIZE - 4]
            ),
            "Count makes it too large"
        );
    }

    @Test
    void rejectsBadComponents() {
        for (final int number : new int[] {0, 0x10000}) {
            Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new TagPart(number, false, false),
                String.format("Component %d", number)
            );
        }
    }

    @Test
    void acceptsLargestComponent() {
        Assertions.assertEquals(
            "ffff",
            HexFormat.of().formatHex(
                LeafItem.of(
                    ItemKey.EMPTY, new TagPart(0xFFFF, false, false),
                    new byte[0]
                ).bytes(),
                3, 5
            ),
            "Component 65535"
        );
    }

    @Test
    void rejectsCorruptItems() {
        final String[][] cases = {
            {"60", "0"},
            {"600000", "1"},
            {"600000", "-1"},
            {"600100", "0"},
            {"6000020000", "0"},
            {"400100", "0"},
            {"400000", "0"},
            {"4002000001", "0"},
            {"4002000000", "0"},
            {"40030200000002", "0"},
        };
        for (final String[] item : cases) {
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> LeafItem.parse(
                    HexFormat.of().parseHex(item[0]), Integer.parseInt(item[1])
                ),
                item[0]
            );
        }
    }

    @Test
    void ordersByKeyThenComponent() {
        final LeafItem one = LeafItem.of(
            LeafItemTest.KEY, TagPart.WHOLE, new byte[0]
        );
        final LeafItem two = LeafItem.of(
            LeafItemTest.KEY, new TagPart(2, false, true), new byte[0]
        );
        Assertions.assertEquals(
            List.of(-1, 1, 0, 1),
            List.of(
                one.order(two), two.order(one), one.order(one),
                Integer.signum(one.order(LeafItem.root()))
            ),
            "Item order"
        );
    }

    @Test
    void comparesByEncoding() {
        Assertions.assertEquals(
            List.of(true, false),
            List.of(
                LeafItem.root().equals(LeafItem.root()),
                LeafItem.root().equals(
                    LeafItem.of(
                        ItemKey.EMPTY, new TagPart(1, false, false),
                        new byte[0]
                    )
                )
            ),
            "Value equality"
        );
    }

    @Test
    void differsFromOtherTypes() {
        Assertions.assertNotEquals(LeafItem.root(), new Object(), "Type");
    }

    @Test
    void hashesDistinctItems() {
        Assertions.assertNotEquals(
            LeafItem.root().hashCode(),
            LeafItem.of(LeafItemTest.KEY, TagPart.WHOLE, new byte[0]).hashCode(),
            "Distinct hashes"
        );
    }

    @Test
    void hashesByEncoding() {
        Assertions.assertEquals(
            LeafItem.root().hashCode(), LeafItem.root().hashCode(), "Hash"
        );
    }

    @Test
    void printsFields() {
        Assertions.assertEquals(
            String.format(
                "LeafItem[key=6162, part=%s, chunk=3 bytes]",
                new TagPart(2, false, true)
            ),
            LeafItem.of(
                LeafItemTest.KEY, new TagPart(2, false, true), new byte[3]
            ).toString(),
            "Text form"
        );
    }

    @Test
    void copiesChunk() {
        final byte[] raw = {1};
        final LeafItem item = LeafItem.of(ItemKey.EMPTY, TagPart.WHOLE, raw);
        raw[0] = 2;
        item.chunk()[0] = 3;
        Assertions.assertEquals(1, item.chunk()[0], "Defensive copies");
    }

    private static String hex(final TableItem item) {
        return HexFormat.of().formatHex(item.bytes());
    }
}
