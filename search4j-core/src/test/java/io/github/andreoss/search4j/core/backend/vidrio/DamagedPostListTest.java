/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.types.DocId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link VidrioPostList} over damaged chunks.
 *
 * @since 0.1.0
 */
final class DamagedPostListTest {

    /**
     * Term of the handcrafted lists.
     */
    private static final String TERM = "a";

    /**
     * Key of the handcrafted lists, in hexadecimal.
     */
    private static final String KEY = "61";

    /**
     * First chunk of two, documents 1 and 3, in hexadecimal.
     */
    private static final String FIRST = "0309003002020104";

    /**
     * Message of a chunk ending early.
     */
    private static final String SHORT =
        "Posting list chunk ends before its last document";

    @Test
    void rejectsDamagedFirstChunks() {
        final Map<String, String> bad = new TreeMap<>();
        bad.put("02", "Collection frequency out of data");
        bad.put("020600", "Last chunk flag out of data");
        bad.put("0206007802", "Last chunk flag is not a flag");
        bad.put("02060031", "Last document out of data");
        bad.put("0206003102", "Data ran out unexpectedly when reading posting list");
        bad.put("0000003100", "Data ran out unexpectedly when reading posting list");
        final List<String> seen = new ArrayList<>(bad.size());
        for (final Map.Entry<String, String> item : bad.entrySet()) {
            final VidrioTable table =
                Readback.crafted(DamagedPostListTest.KEY, item.getKey());
            try (table) {
                seen.add(
                    Assertions.assertThrows(
                        DatabaseCorruptError.class,
                        () -> VidrioPostList.open(table, DamagedPostListTest.TERM),
                        item.getKey()
                    ).getMessage()
                );
            }
        }
        Assertions.assertEquals(
            new ArrayList<>(bad.values()), seen, "Truncated and junk headers"
        );
    }

    @Test
    void rejectsChunkEndingBeforeItsLastDocument() {
        final List<Object> seen = new ArrayList<>(2);
        final VidrioTable table = Readback.crafted(
            DamagedPostListTest.KEY, "0206003109020104"
        );
        try (table) {
            final VidrioPostList list =
                VidrioPostList.open(table, DamagedPostListTest.TERM);
            seen.add(
                DamagedPostListTest.message(() -> list.skipTo(DocId.of(7)))
            );
            final VidrioPostList other =
                VidrioPostList.open(table, DamagedPostListTest.TERM);
            seen.add(
                DamagedPostListTest.message(() -> other.jumpTo(DocId.of(7)))
            );
        }
        Assertions.assertEquals(
            List.of(DamagedPostListTest.SHORT, DamagedPostListTest.SHORT),
            seen,
            "Header claims a later last document"
        );
    }

    @Test
    void rejectsMissingContinuation() {
        final List<Object> seen = new ArrayList<>(4);
        final VidrioTable alone = Readback.crafted(
            DamagedPostListTest.KEY, DamagedPostListTest.FIRST
        );
        try (alone) {
            final VidrioPostList list =
                VidrioPostList.open(alone, DamagedPostListTest.TERM);
            list.next();
            list.next();
            seen.add(DamagedPostListTest.message(list::next));
            seen.add(list.atEnd());
        }
        final VidrioTable table = Readback.crafted(
            DamagedPostListTest.KEY, DamagedPostListTest.FIRST,
            "62", "010300310003"
        );
        try (table) {
            final VidrioPostList list =
                VidrioPostList.open(table, DamagedPostListTest.TERM);
            seen.add(
                DamagedPostListTest.message(() -> list.skipTo(DocId.of(4)))
            );
            seen.add(list.atEnd());
        }
        Assertions.assertEquals(
            List.of(
                "Unexpected end of posting list for 'a'", true,
                "Unexpected end of posting list for 'a'", true
            ),
            seen,
            "No next key, next key of another term"
        );
    }

    @Test
    void rejectsDamagedContinuations() {
        final List<String> keys = List.of(
            "61000003", "61000005", "61000005", "61000005", "610080"
        );
        final List<String> tags = List.of(
            "3101070001", "3100", "3000", "7800", "3101070001"
        );
        final List<String> seen = new ArrayList<>(keys.size());
        for (int idx = 0; idx < keys.size(); ++idx) {
            final VidrioTable table = Readback.crafted(
                DamagedPostListTest.KEY, DamagedPostListTest.FIRST,
                keys.get(idx), tags.get(idx)
            );
            try (table) {
                final VidrioPostList list =
                    VidrioPostList.open(table, DamagedPostListTest.TERM);
                list.next();
                list.next();
                seen.add(DamagedPostListTest.message(list::next));
            }
        }
        Assertions.assertEquals(
            List.of(
                "Document ID in new chunk of postlist (3) is not greater than final document ID in previous chunk (3)",
                "Data ran out unexpectedly when reading posting list",
                "Data ran out unexpectedly when reading posting list",
                "Last chunk flag is not a flag",
                "Bad document number in key 610080"
            ),
            seen,
            "Documents out of order, headers truncated, keys damaged"
        );
    }

    @Test
    void rejectsDamagedChunkOnSkip() {
        final List<Object> seen = new ArrayList<>(2);
        final VidrioTable table = Readback.crafted(
            DamagedPostListTest.KEY, DamagedPostListTest.FIRST, "61000005", "31"
        );
        try (table) {
            final VidrioPostList list =
                VidrioPostList.open(table, DamagedPostListTest.TERM);
            seen.add(
                DamagedPostListTest.message(() -> list.skipTo(DocId.of(5)))
            );
            final VidrioPostList other =
                VidrioPostList.open(table, DamagedPostListTest.TERM);
            seen.add(
                DamagedPostListTest.message(() -> other.jumpTo(DocId.of(5)))
            );
        }
        Assertions.assertEquals(
            List.of("Last document out of data", "Last document out of data"),
            seen,
            "Truncated header of the chunk skipped to"
        );
    }

    private static String message(final Runnable action) {
        String result = "";
        try {
            action.run();
        } catch (final DatabaseCorruptError ex) {
            result = ex.getMessage();
        }
        return result;
    }
}
