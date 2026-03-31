/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.list.VectorPositionList;
import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.ser.BitWriter;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link VidrioPositionList} and {@link PositionTag}: vectors
 * derived from the reference encoder by hand, random round trips, moves
 * and damaged data.
 *
 * @since 0.1.0
 */
final class VidrioPositionListTest {

    /**
     * Positions 5, 8, 10 and 12 encoded, in hexadecimal.
     */
    private static final String FOUR = "0c9501";

    /**
     * Vectors: positions as text, then their encoding in hexadecimal.
     */
    private static final List<List<String>> VECTORS = List.of(
        List.of("7", "07"),
        List.of("3 9", "0903"),
        List.of("1 2 3", "0303"),
        List.of("5 8 10 12", VidrioPositionListTest.FOUR),
        List.of("1 2 3 5 8 13 21 34", "228109181d"),
        List.of("1 1000000", "c0843d0100000000"),
        List.of("0 2", "0200"),
        List.of("2147483646 2147483647", "ffffffff07feffff7f")
    );

    @Test
    void encodesVectorsAsReference() {
        final List<String> seen = new ArrayList<>(8);
        for (final List<String> vector : VidrioPositionListTest.VECTORS) {
            seen.add(
                HexFormat.of().formatHex(
                    PositionTag.encode(
                        VidrioPositionListTest.positions(vector.get(0))
                    )
                )
            );
        }
        final List<String> expected = new ArrayList<>(8);
        for (final List<String> vector : VidrioPositionListTest.VECTORS) {
            expected.add(vector.get(1));
        }
        Assertions.assertEquals(
            expected, seen, "Last position, then the bit stream by hand"
        );
    }

    @Test
    void decodesVectors() {
        final List<String> seen = new ArrayList<>(8);
        for (final List<String> vector : VidrioPositionListTest.VECTORS) {
            final VidrioPositionList list = VidrioPositionList.of(
                HexFormat.of().parseHex(vector.get(1))
            );
            seen.add(
                String.format(
                    "%s/%s/%s", list.approxSize(), list.back(),
                    VidrioPositionListTest.read(list)
                )
            );
        }
        Assertions.assertEquals(
            List.of(
                "1/7/[7]", "2/9/[3, 9]", "3/3/[1, 2, 3]", "4/12/[5, 8, 10, 12]",
                "8/34/[1, 2, 3, 5, 8, 13, 21, 34]", "2/1000000/[1, 1000000]",
                "2/2/[0, 2]",
                "2/2147483647/[2147483646, 2147483647]"
            ),
            seen,
            "Count, last position and every position of each vector"
        );
    }

    @Test
    void skipsAsReference() {
        final List<Object> seen = new ArrayList<>(14);
        final VidrioPositionList list = VidrioPositionListTest.four();
        seen.add(list.skipTo(new TermPos(5)));
        seen.add(list.position());
        seen.add(list.skipTo(new TermPos(9)));
        seen.add(list.position());
        seen.add(list.next());
        seen.add(list.position());
        seen.add(list.skipTo(new TermPos(12)));
        seen.add(list.position());
        seen.add(list.skipTo(new TermPos(13)));
        seen.add(list.atEnd());
        seen.add(VidrioPositionListTest.failure(list::position));
        seen.add(list.next());
        seen.add(list.skipTo(new TermPos(12)));
        seen.add(list.position());
        Assertions.assertEquals(
            List.of(
                true, new TermPos(5), true, new TermPos(10), true,
                new TermPos(12), true, new TermPos(12), false, true,
                InvalidOperationError.class, false, true, new TermPos(12)
            ),
            seen,
            "First position at or after the target, the last reachable again"
        );
    }

    @Test
    void skipsBackwardTargetsAndStaysOnPosition() {
        final List<Object> seen = new ArrayList<>(6);
        final VidrioPositionList list = VidrioPositionListTest.four();
        seen.add(list.skipTo(new TermPos(11)));
        seen.add(list.position());
        seen.add(list.skipTo(TermPos.FIRST));
        seen.add(list.position());
        final VidrioPositionList other = VidrioPositionListTest.four();
        seen.add(other.skipTo(TermPos.FIRST));
        seen.add(other.position());
        Assertions.assertEquals(
            List.of(
                true, new TermPos(12), true, new TermPos(12), true,
                new TermPos(5)
            ),
            seen,
            "A lower target leaves the list where it is"
        );
    }

    @Test
    void handlesEmptyAndSingleLists() {
        final List<Object> seen = new ArrayList<>(14);
        final VidrioPositionList empty = VidrioPositionList.of(new byte[0]);
        seen.add(empty.approxSize());
        seen.add(empty.back());
        seen.add(VidrioPositionListTest.failure(empty::position));
        seen.add(empty.atEnd());
        seen.add(empty.next());
        seen.add(empty.atEnd());
        seen.add(VidrioPositionList.of(new byte[0]).skipTo(TermPos.FIRST));
        final VidrioPositionList single =
            VidrioPositionList.of(HexFormat.of().parseHex("07"));
        seen.add(single.approxSize());
        seen.add(VidrioPositionListTest.failure(single::position));
        seen.add(single.next());
        seen.add(single.position());
        seen.add(single.next());
        seen.add(VidrioPositionListTest.failure(single::position));
        seen.add(
            VidrioPositionList.of(HexFormat.of().parseHex("07"))
                .skipTo(new TermPos(7))
        );
        Assertions.assertEquals(
            List.of(
                TermCount.ZERO, TermPos.FIRST, InvalidOperationError.class,
                false, false, true, false, new TermCount(1),
                InvalidOperationError.class, true, new TermPos(7), false,
                InvalidOperationError.class, true
            ),
            seen,
            "Empty list at its end from the first move, single one reached"
        );
    }

    @Test
    void roundTripsRandomSets() {
        final Random random = new Random(29L);
        final List<String> faults = new ArrayList<>(0);
        for (int round = 0; round < 300; ++round) {
            final List<TermPos> positions =
                VidrioPositionListTest.random(random);
            final byte[] data = PositionTag.encode(positions);
            final List<TermPos> seen =
                VidrioPositionListTest.read(VidrioPositionList.of(data));
            if (!seen.equals(positions)) {
                faults.add(String.format("%d: %s", round, seen));
            }
            faults.addAll(
                VidrioPositionListTest.skips(round, random, positions, data)
            );
        }
        Assertions.assertEquals(
            List.of(), faults, "Random position sets written and read back"
        );
    }

    @Test
    void refusesBadPositions() {
        final List<Object> seen = new ArrayList<>(3);
        seen.add(
            VidrioPositionListTest.failure(
                () -> PositionTag.encode(List.of())
            )
        );
        seen.add(
            VidrioPositionListTest.failure(
                () -> PositionTag.encode(Positions.list(3, 2))
            )
        );
        seen.add(
            VidrioPositionListTest.failure(
                () -> PositionTag.encode(Positions.list(1, 2, 2))
            )
        );
        Assertions.assertEquals(
            List.of(
                InvalidArgumentError.class, InvalidArgumentError.class,
                InvalidArgumentError.class
            ),
            seen,
            "Empty, descending and repeated positions refused"
        );
    }

    @Test
    void reportsDamagedData() {
        final List<Object> seen = new ArrayList<>(6);
        final VidrioPositionList cut =
            VidrioPositionList.of(HexFormat.of().parseHex("0c95"));
        cut.next();
        cut.next();
        seen.add(cut.position());
        seen.add(VidrioPositionListTest.failure(cut::next));
        seen.add(
            VidrioPositionListTest.failure(
                () -> VidrioPositionList.of(HexFormat.of().parseHex("0c95"))
                    .skipTo(new TermPos(10))
            )
        );
        for (final String hex : List.of("0000", "ffffffff0f", "80", "0c")) {
            seen.add(
                VidrioPositionListTest.failure(
                    () -> VidrioPositionList.of(HexFormat.of().parseHex(hex))
                )
            );
        }
        final BitWriter writer = new BitWriter();
        writer.encode(0L, Integer.MAX_VALUE);
        writer.encode(Integer.MAX_VALUE - 1L, Integer.MAX_VALUE);
        final byte[] stream = writer.freeze();
        final byte[] huge = new byte[5 + stream.length];
        System.arraycopy(
            HexFormat.of().parseHex("ffffffff07"), 0, huge, 0, 5
        );
        System.arraycopy(stream, 0, huge, 5, stream.length);
        seen.add(
            VidrioPositionListTest.failure(() -> VidrioPositionList.of(huge))
        );
        Assertions.assertEquals(
            List.of(
                new TermPos(8), DatabaseCorruptError.class,
                DatabaseCorruptError.class, DatabaseCorruptError.class,
                DatabaseCorruptError.class, DatabaseCorruptError.class,
                Void.class, DatabaseCorruptError.class
            ),
            seen,
            "Truncated stream, zero last, oversized last, cut header, count"
        );
    }

    private static VidrioPositionList four() {
        return VidrioPositionList.of(
            HexFormat.of().parseHex(VidrioPositionListTest.FOUR)
        );
    }

    private static List<TermPos> positions(final String text) {
        final List<TermPos> result = new ArrayList<>(8);
        for (final String item : text.split(" ", -1)) {
            result.add(TermPos.parse(item));
        }
        return result;
    }

    private static List<TermPos> read(final VidrioPositionList list) {
        final List<TermPos> result = new ArrayList<>(0);
        while (list.next()) {
            result.add(list.position());
        }
        return result;
    }

    private static List<TermPos> random(final Random random) {
        final SortedSet<TermPos> set = new TreeSet<>();
        final int size = 1 + random.nextInt(300);
        final int gap = 1 << random.nextInt(31);
        while (set.size() < size) {
            set.add(new TermPos(random.nextInt(Math.max(size, gap))));
        }
        return new ArrayList<>(set);
    }

    private static List<String> skips(
        final int round, final Random random, final List<TermPos> positions,
        final byte[] data
    ) {
        final List<String> faults = new ArrayList<>(0);
        final VidrioPositionList list = VidrioPositionList.of(data);
        final VectorPositionList model = new VectorPositionList(positions);
        final int top = positions.get(positions.size() - 1).value();
        boolean more = true;
        for (int idx = 0; more && idx < 40; ++idx) {
            final TermPos target =
                new TermPos(random.nextInt(Math.max(1, top + 2)));
            more = model.skipTo(target);
            if (list.skipTo(target) == more) {
                if (more && !list.position().equals(model.position())) {
                    faults.add(
                        String.format("%d: at %s", round, list.position())
                    );
                    more = false;
                }
            } else {
                faults.add(String.format("%d: skip to %s", round, target));
                more = false;
            }
        }
        return faults;
    }

    private static Class<?> failure(final Runnable action) {
        Class<?> result = Void.class;
        try {
            action.run();
        } catch (final DatabaseCorruptError | InvalidArgumentError
            | InvalidOperationError ex) {
            result = ex.getClass();
        }
        return result;
    }
}
