/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseClosedError;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of the cursor of {@link TableWriter} over uncommitted changes.
 *
 * @since 0.1.0
 */
final class TableWriterCursorTest {

    @Test
    void readsUncommittedEntries() {
        final TableWriter writer = TableWriterCursorTest.writer();
        writer.add(Trees.key("b"), Trees.key("two"));
        writer.add(Trees.key("a"), Trees.key("one"));
        writer.add(Trees.key("c"), Trees.key("three"));
        final List<Object> seen = new ArrayList<>(6);
        seen.add(TableWriterCursorTest.keys(writer.cursor()));
        seen.add(
            new String(
                writer.get(Trees.key("b")).orElseThrow(),
                StandardCharsets.US_ASCII
            )
        );
        seen.add(writer.get(Trees.key("d")).isPresent());
        seen.add(writer.contains(Trees.key("c")));
        seen.add(writer.contains(Trees.key("d")));
        writer.delete(Trees.key("a"));
        seen.add(TableWriterCursorTest.keys(writer.cursor()));
        Assertions.assertEquals(
            List.of(
                List.of("a", "b", "c"), "two", false, true, false,
                List.of("b", "c")
            ),
            seen,
            "Cursor sees every change made before it"
        );
    }

    @Test
    void readsAcrossCommitAndCancel() {
        final TableWriter writer = TableWriterCursorTest.writer();
        writer.add(Trees.key("a"), Trees.key("one"));
        final TableState state = writer.commit(new RevisionNumber(1L));
        writer.add(Trees.key("b"), Trees.key("two"));
        final List<Object> seen = new ArrayList<>(3);
        seen.add(TableWriterCursorTest.keys(writer.cursor()));
        writer.cancel(state);
        seen.add(TableWriterCursorTest.keys(writer.cursor()));
        writer.add(Trees.key("c"), Trees.key("three"));
        writer.commit(new RevisionNumber(2L));
        seen.add(TableWriterCursorTest.keys(writer.cursor()));
        Assertions.assertEquals(
            List.of(List.of("a", "b"), List.of("a"), List.of("a", "c")),
            seen,
            "Changes dropped on cancel, kept on commit"
        );
    }

    @Test
    void readsEmptyTable() {
        final TableWriter writer = TableWriterCursorTest.writer();
        final TableCursor cursor = writer.cursor();
        cursor.rewind();
        Assertions.assertEquals(
            List.of(false, false, false),
            List.of(
                cursor.next(), writer.get(Trees.key("a")).isPresent(),
                writer.contains(Trees.key("a"))
            ),
            "Only the root item"
        );
    }

    @Test
    void readsDeepUncommittedTree() {
        final Workload work = new Workload(3L, BlockSize.MIN);
        final TableWriter writer = TableWriter.create(work.blocks(), 0);
        work.apply(writer, 2000, 10);
        final List<String> found = new ArrayList<>(work.model().size());
        final TableCursor cursor = writer.cursor();
        cursor.rewind();
        while (cursor.next()) {
            found.add(
                String.format(
                    "%s=%s", HexFormat.of().formatHex(cursor.key().data()),
                    HexFormat.of().formatHex(cursor.tag())
                )
            );
        }
        Assertions.assertEquals(
            work.expected(), found, "Every entry of a multi-level tree"
        );
    }

    @Test
    void readsDeepChangesWithoutWriting() {
        final Map<Long, byte[]> data = new HashMap<>();
        final TableWriter writer =
            TableWriter.create(new MemoryBlocks(data, BlockSize.MIN), 0);
        for (int idx = 0; idx < 600; ++idx) {
            writer.add(Trees.key(String.format("k%05d", idx)), new byte[40]);
        }
        writer.commit(new RevisionNumber(1L));
        writer.add(Trees.key("k00300x"), Trees.key("new"));
        writer.delete(Trees.key("k00450"));
        final Map<Long, String> before = TableWriterCursorTest.dump(data);
        final TableCursor cursor = writer.cursor();
        int count = 0;
        cursor.rewind();
        while (cursor.next()) {
            count += 1;
        }
        Assertions.assertEquals(
            List.of(600, "new", false, true, true),
            List.of(
                count,
                new String(
                    writer.get(Trees.key("k00300x")).orElseThrow(),
                    StandardCharsets.US_ASCII
                ),
                writer.contains(Trees.key("k00450")),
                writer.contains(Trees.key("k00599")),
                TableWriterCursorTest.dump(data).equals(before)
            ),
            "Pending blocks of a multi-level tree read from memory, none written"
        );
    }

    @Test
    void seesChangesMadeAfterOpening() {
        final TableWriter writer = TableWriterCursorTest.filled();
        final TableCursor cursor = writer.cursor();
        cursor.rewind();
        final List<String> seen = TableWriterCursorTest.steps(cursor, 100);
        for (int idx = 0; idx < 600; idx += 2) {
            writer.add(Trees.key(String.format("k%05dx", idx)), new byte[60]);
        }
        for (int idx = 200; idx < 500; ++idx) {
            writer.delete(Trees.key(String.format("k%05d", idx)));
        }
        seen.addAll(TableWriterCursorTest.steps(cursor, Integer.MAX_VALUE));
        final List<String> expected = new ArrayList<>(550);
        for (int idx = 0; idx < 600; ++idx) {
            if (idx < 200 || idx >= 500) {
                expected.add(String.format("k%05d", idx));
            }
            if (idx >= 100 && idx % 2 == 0) {
                expected.add(String.format("k%05dx", idx));
            }
        }
        Assertions.assertEquals(
            expected, seen,
            "Entries added after the cursor opened are seen, deleted ones not"
        );
    }

    @Test
    void movesPastDeletedCurrentEntry() {
        final TableWriter writer = TableWriterCursorTest.filled();
        final TableCursor cursor = writer.cursor();
        cursor.seek(Trees.key("k00300"));
        cursor.tag();
        for (int idx = 300; idx < 320; ++idx) {
            writer.delete(Trees.key(String.format("k%05d", idx)));
        }
        Assertions.assertEquals(
            List.of("k00320", "k00321"),
            TableWriterCursorTest.steps(cursor, 2),
            "Next entry after a deleted current entry"
        );
    }

    @Test
    void returnsToCommittedEntriesAfterCancel() {
        final TableWriter writer = TableWriterCursorTest.writer();
        for (int idx = 0; idx < 600; ++idx) {
            writer.add(Trees.key(String.format("k%05d", idx)), new byte[40]);
        }
        final TableState state = writer.commit(new RevisionNumber(1L));
        final TableCursor cursor = writer.cursor();
        cursor.seek(Trees.key("k00010"));
        for (int idx = 0; idx < 600; ++idx) {
            writer.add(Trees.key(String.format("k%05dy", idx)), new byte[40]);
        }
        final List<String> seen = TableWriterCursorTest.steps(cursor, 2);
        writer.cancel(state);
        seen.addAll(TableWriterCursorTest.steps(cursor, Integer.MAX_VALUE));
        Assertions.assertEquals(
            List.of(590, "k00010y", "k00011", "k00012", "k00599"),
            List.of(
                seen.size(), seen.get(0), seen.get(1), seen.get(2),
                seen.get(589)
            ),
            "Entries dropped by a cancel are not seen"
        );
    }

    @Test
    void findsEntriesAddedAfterOpening() {
        final TableWriter writer = TableWriterCursorTest.filled();
        final TableCursor exact = writer.cursor();
        final TableCursor least = writer.cursor();
        final TableCursor most = writer.cursor();
        exact.rewind();
        least.rewind();
        most.rewind();
        for (int idx = 0; idx < 600; idx += 3) {
            writer.add(Trees.key(String.format("k%05dz", idx)), Trees.key("new"));
        }
        final boolean found = exact.exact(Trees.key("k00450z"));
        least.seekAtLeast(Trees.key("k00450y"));
        most.seek(Trees.key("k00450zz"));
        Assertions.assertEquals(
            List.of(true, "new", "k00450z", "new", "k00450z"),
            List.of(
                found, new String(exact.tag(), StandardCharsets.US_ASCII),
                new String(least.key().data(), StandardCharsets.US_ASCII),
                new String(least.tag(), StandardCharsets.US_ASCII),
                new String(most.key().data(), StandardCharsets.US_ASCII)
            ),
            "Entries added after the cursors opened are found"
        );
    }

    @Test
    void sharesStoreOfWriterBlocks() {
        final MemoryBlocks blocks = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        final WriterBlocks view = new WriterBlocks(
            blocks,
            new Spine(
                blocks,
                FreeBlocks.of(blocks, TableState.initial(BlockSize.MIN, 0).free())
            )
        );
        view.close();
        Assertions.assertEquals(
            List.of(BlockSize.MIN, 1),
            List.of(view.size(), blocks.closes()),
            "Size and close of the store"
        );
    }

    @Test
    void rejectsCursorAfterClose() {
        final TableWriter writer = TableWriterCursorTest.writer();
        writer.close();
        Assertions.assertThrows(
            DatabaseClosedError.class, writer::cursor, "Closed"
        );
    }

    private static Map<Long, String> dump(final Map<Long, byte[]> data) {
        final Map<Long, String> copy = new TreeMap<>();
        for (final Map.Entry<Long, byte[]> entry : data.entrySet()) {
            copy.put(entry.getKey(), HexFormat.of().formatHex(entry.getValue()));
        }
        return copy;
    }

    private static TableWriter filled() {
        final TableWriter writer = TableWriterCursorTest.writer();
        for (int idx = 0; idx < 600; ++idx) {
            writer.add(Trees.key(String.format("k%05d", idx)), new byte[40]);
        }
        writer.commit(new RevisionNumber(1L));
        return writer;
    }

    private static List<String> steps(final TableCursor cursor, final int most) {
        final List<String> keys = new ArrayList<>(0);
        while (keys.size() < most && cursor.next()) {
            keys.add(new String(cursor.key().data(), StandardCharsets.US_ASCII));
            cursor.tag();
        }
        return keys;
    }

    private static TableWriter writer() {
        return TableWriter.create(
            new MemoryBlocks(new HashMap<>(), BlockSize.MIN), 0
        );
    }

    private static List<String> keys(final TableCursor cursor) {
        final List<String> keys = new ArrayList<>(0);
        cursor.rewind();
        while (cursor.next()) {
            keys.add(new String(cursor.key().data(), StandardCharsets.US_ASCII));
        }
        return keys;
    }
}
