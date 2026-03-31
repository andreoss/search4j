/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link PositionTable}, {@link PositionWriter} and
 * {@link PositionKeys}: keys and tags as the reference writes them,
 * lookups, and the positions reached through term and posting lists.
 *
 * @since 0.1.0
 */
final class PositionTableTest {

    /**
     * Term with positions.
     */
    private static final String TERM = "foo";

    @Test
    void writesKeysAndTagsAsReference() {
        final Positions positions = Positions.of();
        positions.set(1, PositionTableTest.TERM, Positions.list(5, 8, 10, 12));
        positions.set(
            40_000, String.format("a%sb", Character.toString(0)),
            Positions.list(7)
        );
        final TableCursor cursor = positions.writer().cursor();
        cursor.rewind();
        final List<String> seen = new ArrayList<>(2);
        while (cursor.next()) {
            seen.add(
                String.format(
                    "%s=%s", cursor.key(), HexFormat.of().formatHex(cursor.tag())
                )
            );
        }
        seen.add(
            HexFormat.of().formatHex(
                PositionKeys.of(DocId.of(1), PositionTableTest.TERM)
            )
        );
        Assertions.assertEquals(
            List.of(
                "6100ff6200809c40=07", "666f6f000001=0c9501", "666f6f000001"
            ),
            seen,
            "Term with its terminator, sortable document, encoded positions"
        );
    }

    @Test
    void looksUpSetsAndDeletes() {
        final List<Object> seen = new ArrayList<>(12);
        final Positions positions = Positions.of();
        positions.set(1, PositionTableTest.TERM, Positions.list(5, 8, 10, 12));
        positions.set(2, PositionTableTest.TERM, Positions.list(3));
        final PositionTable first = positions.commit();
        seen.add(first.count(DocId.of(1), PositionTableTest.TERM));
        seen.add(first.count(DocId.of(2), PositionTableTest.TERM));
        seen.add(first.count(DocId.of(3), PositionTableTest.TERM));
        seen.add(first.count(DocId.of(1), "fo"));
        seen.add(first.contains(DocId.of(1), PositionTableTest.TERM));
        seen.add(first.contains(DocId.of(1), "fo"));
        seen.add(first.open(DocId.of(3), PositionTableTest.TERM).next());
        seen.add(
            PositionTableTest.read(first.open(DocId.of(1), PositionTableTest.TERM))
        );
        positions.set(1, PositionTableTest.TERM, Positions.list(4));
        seen.add(positions.delete(2, PositionTableTest.TERM));
        seen.add(positions.delete(2, PositionTableTest.TERM));
        final PositionTable second = positions.commit();
        seen.add(
            PositionTableTest.read(second.open(DocId.of(1), PositionTableTest.TERM))
        );
        seen.add(second.contains(DocId.of(2), PositionTableTest.TERM));
        Assertions.assertEquals(
            List.of(
                new TermCount(4), new TermCount(1), TermCount.ZERO,
                TermCount.ZERO, true, false, false,
                Positions.list(5, 8, 10, 12), true, false, Positions.list(4),
                false
            ),
            seen,
            "Counts and lists by document and term, replaced and deleted"
        );
    }

    @Test
    void reachesPositionsThroughTermList() {
        final List<Object> seen = new ArrayList<>(6);
        final Documents docs = Documents.of(3L);
        docs.add(
            1, Map.of("on", new TermCount(2), "the", new TermCount(1))
        );
        docs.add(2, Map.of("on", new TermCount(1)));
        docs.positions(1, "on", Positions.list(3, 7));
        docs.positions(2, "on", Positions.list(1));
        docs.commit();
        final VidrioTermList list = docs.open(DocId.of(1));
        list.next();
        seen.add(list.term());
        seen.add(list.positionCount());
        seen.add(PositionTableTest.read(list.positionList()));
        seen.add(list.heldPositions().isPresent());
        list.next();
        seen.add(list.positionCount());
        seen.add(list.positionList().next());
        Assertions.assertEquals(
            List.of(
                "on", new TermCount(2), Positions.list(3, 7), false,
                TermCount.ZERO, false
            ),
            seen,
            "Positions of the document and term, none for an unpositioned term"
        );
    }

    @Test
    void reachesPositionsThroughPostList() {
        final List<Object> seen = new ArrayList<>(5);
        final Positions positions = Positions.of();
        positions.set(1, "a", Positions.list(2, 4));
        positions.set(3, "a", Positions.list(9));
        final VidrioTable table = Readback.crafted("61", "0206003102020104");
        try (table) {
            final VidrioPostList list =
                VidrioPostList.open(table, positions.commit(), "a");
            list.next();
            seen.add(PositionTableTest.read(list.readPositionList()));
            final PositionList open = list.openPositionList();
            seen.add(open.next());
            seen.add(open.position());
            list.next();
            seen.add(PositionTableTest.read(list.readPositionList()));
            seen.add(
                PositionTableTest.read(
                    list.openNearby("a", true).orElseThrow().skipTo(
                        DocId.of(3), 0.0
                    ).readPositionList()
                )
            );
        }
        Assertions.assertEquals(
            List.of(
                Positions.list(2, 4), true, new TermPos(2), Positions.list(9),
                Positions.list(9)
            ),
            seen,
            "Positions of the current document, nearby lists share the table"
        );
    }

    private static List<TermPos> read(final PositionList list) {
        final List<TermPos> result = new ArrayList<>(0);
        while (list.next()) {
            result.add(list.position());
        }
        return result;
    }
}
