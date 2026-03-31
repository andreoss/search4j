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
    void rejectsCursorAfterClose() {
        final TableWriter writer = TableWriterCursorTest.writer();
        writer.close();
        Assertions.assertThrows(
            DatabaseClosedError.class, writer::cursor, "Closed"
        );
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
