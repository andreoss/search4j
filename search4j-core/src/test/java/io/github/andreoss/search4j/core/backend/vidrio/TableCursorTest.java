/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.DatabaseModifiedError;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.ser.Zlib;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests of {@link TableCursor} over multi-level trees.
 *
 * @since 0.1.0
 */
final class TableCursorTest {

    /**
     * Number of entries of the large tree.
     */
    private static final int COUNT = 300;

    @Test
    void iteratesDeepTreeInOrder() {
        final List<Object> seen = new ArrayList<>(TableCursorTest.COUNT + 2);
        try (VidrioTable table = TableCursorTest.table(false, 3)) {
            final TableCursor cursor = table.cursor();
            cursor.rewind();
            seen.add(cursor.key().length());
            seen.add(table.root().level() > 2);
            while (cursor.next()) {
                seen.add(TableCursorTest.keyText(cursor.key()));
            }
        }
        final List<Object> expected = new ArrayList<>(seen.size());
        expected.add(0);
        expected.add(true);
        expected.addAll(TableCursorTest.keys());
        Assertions.assertEquals(expected, seen, "Empty key, then all keys");
    }

    @Test
    void reassemblesMultiPartTags() {
        Assertions.assertEquals(
            TableCursorTest.tags(), TableCursorTest.readAll(false),
            "Plain tags"
        );
    }

    @Test
    void inflatesCompressedTags() {
        Assertions.assertEquals(
            TableCursorTest.tags(), TableCursorTest.readAll(true),
            "Compressed tags"
        );
    }

    @Test
    void looksUpEveryKey() {
        final List<String> found = new ArrayList<>(TableCursorTest.COUNT);
        try (VidrioTable table = TableCursorTest.table(true, 4)) {
            for (int idx = 0; idx < TableCursorTest.COUNT; ++idx) {
                found.add(
                    TableCursorTest.text(
                        table.get(Trees.key(TableCursorTest.name(idx)))
                            .orElseThrow()
                    )
                );
            }
        }
        Assertions.assertEquals(TableCursorTest.tags(), found, "Every tag");
    }

    @Test
    void findsAbsentKeysAbsent() {
        try (VidrioTable table = TableCursorTest.table(false, 3)) {
            Assertions.assertEquals(
                List.of(false, false, false, true),
                List.of(
                    table.get(Trees.key("k00010a")).isPresent(),
                    table.get(Trees.key("zzz")).isPresent(),
                    table.contains(Trees.key("a")),
                    table.contains(Trees.key("k00299"))
                ),
                "Between, after, before and last"
            );
        }
    }

    @Test
    void seeksToEntryAtOrBefore() {
        final List<Object> seen = new ArrayList<>(7);
        try (VidrioTable table = TableCursorTest.table(false, 3)) {
            final TableCursor cursor = table.cursor();
            seen.add(cursor.seek(Trees.key("k00010a")));
            seen.add(TableCursorTest.keyText(cursor.key()));
            seen.add(TableCursorTest.text(cursor.tag()));
            seen.add(cursor.next());
            seen.add(TableCursorTest.keyText(cursor.key()));
            seen.add(cursor.seek(Trees.key("k00042")));
            seen.add(TableCursorTest.keyText(cursor.key()));
        }
        Assertions.assertEquals(
            List.of(
                false, "k00010", TableCursorTest.text(TableCursorTest.tag(10)),
                true, "k00011", true, "k00042"
            ),
            seen,
            "Seek lands on the entry at or before the key"
        );
    }

    @Test
    void movesToNextKeyAfterSeekWithoutTag() {
        final List<Object> seen = new ArrayList<>(2);
        try (VidrioTable table = TableCursorTest.table(false, 3)) {
            final TableCursor cursor = table.cursor();
            cursor.seek(Trees.key("k00100z"));
            seen.add(cursor.next());
            seen.add(TableCursorTest.keyText(cursor.key()));
        }
        Assertions.assertEquals(List.of(true, "k00101"), seen, "Next key");
    }

    @Test
    void seeksToEntryAtOrAfter() {
        final List<Object> seen = new ArrayList<>(7);
        try (VidrioTable table = TableCursorTest.table(false, 3)) {
            final TableCursor cursor = table.cursor();
            seen.add(cursor.seekAtLeast(Trees.key("k00010a")));
            seen.add(TableCursorTest.keyText(cursor.key()));
            seen.add(TableCursorTest.text(cursor.tag()));
            seen.add(cursor.seekAtLeast(Trees.key("k00020")));
            seen.add(TableCursorTest.keyText(cursor.key()));
            seen.add(cursor.seekAtLeast(Trees.key("z")));
            seen.add(cursor.afterEnd());
        }
        Assertions.assertEquals(
            List.of(
                false, "k00011", TableCursorTest.text(TableCursorTest.tag(11)),
                true, "k00020", false, true
            ),
            seen,
            "Seek lands on the entry at or after the key"
        );
    }

    @Test
    void seeksToEntryBefore() {
        final List<Object> seen = new ArrayList<>(5);
        try (VidrioTable table = TableCursorTest.table(false, 3)) {
            final TableCursor cursor = table.cursor();
            cursor.seekBefore(Trees.key("k00020"));
            seen.add(TableCursorTest.keyText(cursor.key()));
            seen.add(TableCursorTest.text(cursor.tag()));
            cursor.seekBefore(Trees.key("k00020a"));
            seen.add(TableCursorTest.keyText(cursor.key()));
            cursor.seekBefore(new byte[0]);
            seen.add(cursor.next());
            seen.add(TableCursorTest.keyText(cursor.key()));
        }
        Assertions.assertEquals(
            List.of(
                "k00019", TableCursorTest.text(TableCursorTest.tag(19)),
                "k00020", true, "k00000"
            ),
            seen,
            "Entry before the key"
        );
    }

    @Test
    void truncatesOverlongKey() {
        final String key = "k".repeat(ItemKey.MAX_LENGTH);
        final byte[] longer = Trees.key(key.concat("k"));
        final List<Object> seen = new ArrayList<>(5);
        try (VidrioTable table = TableCursorTest.single(key)) {
            final TableCursor cursor = table.cursor();
            seen.add(cursor.seek(longer));
            seen.add(TableCursorTest.keyText(cursor.key()));
            seen.add(cursor.seekAtLeast(longer));
            seen.add(cursor.afterEnd());
            seen.add(table.contains(Trees.key(key)));
        }
        Assertions.assertEquals(
            List.of(false, key, false, true, true), seen,
            "Longer key goes to its truncation"
        );
    }

    @Test
    void movesOnAfterExactLookup() {
        final List<Object> seen = new ArrayList<>(6);
        try (VidrioTable table = TableCursorTest.table(true, 3)) {
            final TableCursor cursor = table.cursor();
            seen.add(cursor.exact(Trees.key("k00150")));
            seen.add(TableCursorTest.text(cursor.tag()));
            seen.add(cursor.next());
            seen.add(TableCursorTest.keyText(cursor.key()));
            seen.add(cursor.exact(Trees.key("k00299")));
            seen.add(cursor.next());
        }
        Assertions.assertEquals(
            List.of(
                true, TableCursorTest.text(TableCursorTest.tag(150)), true,
                "k00151", true, false
            ),
            seen,
            "Exact lookup then next"
        );
    }

    @Test
    void missesExactLookups() {
        try (VidrioTable table = TableCursorTest.table(false, 3)) {
            final TableCursor cursor = table.cursor();
            Assertions.assertEquals(
                List.of(false, false),
                List.of(
                    cursor.exact(Trees.key("k00150a")),
                    cursor.exact(Trees.key("k".repeat(256)))
                ),
                "Misses"
            );
        }
    }

    @Test
    void refusesTagAfterExactMiss() {
        try (VidrioTable table = TableCursorTest.table(false, 3)) {
            final TableCursor cursor = table.cursor();
            cursor.exact(Trees.key("k00150a"));
            Assertions.assertThrows(
                InvalidOperationError.class, cursor::tag, "No tag"
            );
        }
    }

    @Test
    void doesNotMoveFreshCursor() {
        try (VidrioTable table = TableCursorTest.table(false, 3)) {
            Assertions.assertFalse(table.cursor().next(), "Fresh cursor");
        }
    }

    @Test
    void refusesKeyOfFreshCursor() {
        try (VidrioTable table = TableCursorTest.table(false, 3)) {
            Assertions.assertThrows(
                InvalidOperationError.class, table.cursor()::key, "No key"
            );
        }
    }

    @Test
    void staysAfterEnd() {
        try (VidrioTable table = TableCursorTest.single("a")) {
            final TableCursor cursor = table.cursor();
            cursor.seek(Trees.key("a"));
            Assertions.assertEquals(
                List.of(false, true, false),
                List.of(cursor.next(), cursor.afterEnd(), cursor.next()),
                "Past the last entry"
            );
        }
    }

    @Test
    void refusesTagAfterEnd() {
        try (VidrioTable table = TableCursorTest.single("a")) {
            final TableCursor cursor = table.cursor();
            cursor.seek(Trees.key("a"));
            cursor.next();
            Assertions.assertThrows(
                InvalidOperationError.class, cursor::tag, "No tag"
            );
        }
    }

    @Test
    void stepsBackToFirstComponent() {
        final List<String> seen = new ArrayList<>(2);
        try (VidrioTable table = TableCursorTest.table(false, 3)) {
            final TableCursor cursor = table.cursor();
            cursor.seek(Trees.key("k00007z"));
            seen.add(TableCursorTest.text(cursor.tag()));
            seen.add(TableCursorTest.text(cursor.tag()));
        }
        final String tag = TableCursorTest.text(TableCursorTest.tag(7));
        Assertions.assertEquals(List.of(tag, tag), seen, "Whole, cached");
    }

    @Test
    void cachesBlocksOnPath() {
        final MemoryBlocks blocks = TableCursorTest.blocks(false, 3);
        final List<Integer> seen = new ArrayList<>(3);
        try (VidrioTable table = TableCursorTest.over(blocks)) {
            final int start = blocks.reads();
            table.contains(Trees.key("k00001"));
            seen.add(blocks.reads() - start);
            table.contains(Trees.key("k00001"));
            seen.add(blocks.reads() - start);
            seen.add(table.root().level());
        }
        Assertions.assertEquals(
            List.of(seen.get(2), seen.get(2), seen.get(2)), seen,
            "One read per level, then none"
        );
    }

    @Test
    void rejectsChildOfWrongLevel() {
        final MemoryBlocks blocks = TableCursorTest.blocks(false, 3);
        blocks.bytes(0L)[4] = 1;
        try (VidrioTable table = TableCursorTest.over(blocks)) {
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> table.contains(new byte[0]),
                "Leaf marked level 1"
            );
        }
    }

    @Test
    void rejectsChildNewerThanParent() {
        final MemoryBlocks blocks = TableCursorTest.blocks(false, 3);
        blocks.bytes(0L)[3] = 8;
        try (VidrioTable table = TableCursorTest.over(blocks)) {
            Assertions.assertThrows(
                DatabaseModifiedError.class,
                () -> table.contains(new byte[0]),
                "Leaf of a later revision"
            );
        }
    }

    @Test
    void acceptsChildOfOlderRevision() {
        final MemoryBlocks blocks = TableCursorTest.blocks(false, 3);
        blocks.bytes(0L)[3] = 6;
        try (VidrioTable table = TableCursorTest.over(blocks)) {
            Assertions.assertTrue(table.contains(new byte[0]), "Older leaf");
        }
    }

    @Test
    void rejectsTagWithoutLastComponent() {
        final VidrioTable table = TableCursorTest.damaged(
            LeafItem.of(
                ItemKey.of(Trees.key("a")), new TagPart(1, false, false),
                new byte[] {1}
            )
        );
        try (table) {
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> table.get(Trees.key("a")),
                "Continuation missing"
            );
        }
    }

    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void rejectsTooFewCompressedChunks() {
        final byte[] packed = Zlib.compress(TableCursorTest.tag(5));
        final VidrioTable table = TableCursorTest.damaged(
            LeafItem.of(
                ItemKey.of(Trees.key("a")), new TagPart(1, true, true),
                Arrays.copyOf(packed, packed.length / 2)
            )
        );
        try (table) {
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> table.get(Trees.key("a")),
                "Stream continues past last item"
            );
        }
    }

    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void rejectsEmptyCompressedTag() {
        final VidrioTable table = TableCursorTest.damaged(
            LeafItem.of(
                ItemKey.of(Trees.key("a")), new TagPart(1, true, true),
                new byte[0]
            )
        );
        try (table) {
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> table.get(Trees.key("a")),
                "No compressed bytes"
            );
        }
    }

    @Test
    void rejectsTooManyCompressedChunks() {
        final VidrioTable table = TableCursorTest.damaged(
            LeafItem.of(
                ItemKey.of(Trees.key("a")), new TagPart(1, true, false),
                Zlib.compress(TableCursorTest.tag(5))
            ),
            LeafItem.of(
                ItemKey.of(Trees.key("a")), new TagPart(2, true, true),
                new byte[] {1}
            )
        );
        try (table) {
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> table.get(Trees.key("a")),
                "Stream ends before last item"
            );
        }
    }

    @Test
    void rejectsBadCompressedData() {
        final VidrioTable table = TableCursorTest.damaged(
            LeafItem.of(
                ItemKey.of(Trees.key("a")), new TagPart(1, true, true),
                new byte[] {(byte) 0xFF, (byte) 0xFF, 0}
            )
        );
        try (table) {
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> table.get(Trees.key("a")),
                "Not deflate data"
            );
        }
    }

    @Test
    void closesSourceWhenRootUnreadable() {
        final MemoryBlocks blocks = TableCursorTest.blocks(false, 3);
        Assertions.assertAll(
            () -> Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> VidrioTable.over(blocks, TableCursorTest.pastEnd(blocks)),
                "Root past the end"
            ),
            () -> Assertions.assertEquals(1, blocks.closes(), "Closed")
        );
    }

    @Test
    void closesSourceOnBlockSizeMismatch() {
        final MemoryBlocks blocks = TableCursorTest.blocks(false, 3);
        Assertions.assertAll(
            () -> Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> VidrioTable.over(
                    blocks, TableRoot.empty(BlockSize.DEFAULT, Trees.REVISION)
                ),
                "Different block size"
            ),
            () -> Assertions.assertEquals(1, blocks.closes(), "Closed")
        );
    }

    private static TableRoot pastEnd(final MemoryBlocks blocks) {
        final TableRoot root = Trees.root(blocks);
        return new TableRoot(
            new BlockNumber(blocks.count()), root.level(), root.size(),
            root.revision(), false
        );
    }

    private static VidrioTable damaged(final LeafItem... items) {
        final List<LeafItem> all = new ArrayList<>(items.length + 1);
        all.add(LeafItem.root());
        all.addAll(Arrays.asList(items));
        return TableCursorTest.over(Trees.tree(all, 8, BlockSize.MIN));
    }

    private static List<String> readAll(final boolean compress) {
        final List<String> tags = new ArrayList<>(TableCursorTest.COUNT);
        try (VidrioTable table = TableCursorTest.table(compress, 3)) {
            final TableCursor cursor = table.cursor();
            cursor.rewind();
            while (cursor.next()) {
                tags.add(TableCursorTest.text(cursor.tag()));
            }
        }
        return tags;
    }

    private static VidrioTable single(final String key) {
        return TableCursorTest.over(
            Trees.tree(
                Trees.items(List.of(key), List.of(new byte[] {1}), false),
                8, BlockSize.MIN
            )
        );
    }

    private static VidrioTable table(final boolean compress, final int fanout) {
        return TableCursorTest.over(TableCursorTest.blocks(compress, fanout));
    }

    private static VidrioTable over(final MemoryBlocks blocks) {
        return VidrioTable.over(blocks, Trees.root(blocks));
    }

    private static MemoryBlocks blocks(
        final boolean compress, final int fanout
    ) {
        final List<byte[]> tags = new ArrayList<>(TableCursorTest.COUNT);
        for (int idx = 0; idx < TableCursorTest.COUNT; ++idx) {
            tags.add(TableCursorTest.tag(idx));
        }
        return Trees.tree(
            Trees.items(TableCursorTest.keys(), tags, compress),
            fanout, BlockSize.MIN
        );
    }

    private static List<String> keys() {
        final List<String> keys = new ArrayList<>(TableCursorTest.COUNT);
        for (int idx = 0; idx < TableCursorTest.COUNT; ++idx) {
            keys.add(TableCursorTest.name(idx));
        }
        return keys;
    }

    private static List<String> tags() {
        final List<String> tags = new ArrayList<>(TableCursorTest.COUNT);
        for (int idx = 0; idx < TableCursorTest.COUNT; ++idx) {
            tags.add(TableCursorTest.text(TableCursorTest.tag(idx)));
        }
        return tags;
    }

    private static String name(final int idx) {
        return String.format("k%05d", idx);
    }

    private static byte[] tag(final int idx) {
        return "tag ".repeat(idx % 9).concat(Integer.toString(idx))
            .getBytes(StandardCharsets.US_ASCII);
    }

    private static String text(final byte[] bytes) {
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static String keyText(final ItemKey key) {
        return TableCursorTest.text(key.data());
    }
}
