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
