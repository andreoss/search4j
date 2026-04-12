/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the matcher.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.types.DocId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unions, exclusive unions and intersections of many members match the
 * documents in any member, in an odd number of members and in every
 * member, walked by stepping and by skipping.
 *
 * @since 0.1.0
 */
final class MultiwayPostListTest {

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    void matchesTheMemberSets(final int seed) {
        final Random random = new Random(seed);
        for (int round = 0; round < 20; round = round + 1) {
            final int[][] sets = new int[3 + random.nextInt(7)][];
            for (int idx = 0; idx < sets.length; idx = idx + 1) {
                sets[idx] = IntStream.rangeClosed(1, 9)
                    .filter(doc -> random.nextInt(3) > 0).toArray();
            }
            final String name = String.format("seed %d round %d", seed, round);
            final List<DocId> union = MultiwayPostListTest.counted(sets, 1, sets.length);
            final List<DocId> parity = MultiwayPostListTest.odd(sets);
            final List<DocId> every =
                MultiwayPostListTest.counted(sets, sets.length, sets.length);
            Assertions.assertEquals(
                List.of(union, union, parity, parity, every, every),
                List.of(
                    BooleanPostListFixture.drain(
                        new BoolOrPostList(
                            MultiwayPostListTest.fakes(sets),
                            BooleanPostListFixture.size()
                        )
                    ),
                    BooleanPostListFixture.skip(
                        new BoolOrPostList(
                            MultiwayPostListTest.fakes(sets),
                            BooleanPostListFixture.size()
                        )
                    ),
                    BooleanPostListFixture.drain(
                        new XorPostList(
                            MultiwayPostListTest.fakes(sets),
                            BooleanPostListFixture.size(), () -> { }
                        )
                    ),
                    BooleanPostListFixture.skip(
                        new XorPostList(
                            MultiwayPostListTest.fakes(sets),
                            BooleanPostListFixture.size(), () -> { }
                        )
                    ),
                    BooleanPostListFixture.drain(
                        new AndPostList(
                            MultiwayPostListTest.fakes(sets), () -> { }
                        )
                    ),
                    BooleanPostListFixture.skip(
                        new AndPostList(
                            MultiwayPostListTest.fakes(sets), () -> { }
                        )
                    )
                ),
                name
            );
        }
    }

    private static List<BooleanPostListFixtureFake> fakes(final int[]... sets) {
        final List<BooleanPostListFixtureFake> result =
            new ArrayList<>(sets.length);
        for (int idx = 0; idx < sets.length; idx = idx + 1) {
            result.add(
                BooleanPostListFixture.fake(String.format("m%d", idx), sets[idx])
            );
        }
        return result;
    }

    private static List<DocId> counted(
        final int[][] sets, final int least, final int most
    ) {
        return IntStream.rangeClosed(1, 9).filter(
            doc -> {
                final long hits = MultiwayPostListTest.hits(sets, doc);
                return hits >= least && hits <= most;
            }
        ).mapToObj(DocId::of).toList();
    }

    private static List<DocId> odd(final int[]... sets) {
        return IntStream.rangeClosed(1, 9)
            .filter(doc -> MultiwayPostListTest.hits(sets, doc) % 2 == 1)
            .mapToObj(DocId::of).toList();
    }

    private static long hits(final int[][] sets, final int doc) {
        return Arrays.stream(sets)
            .filter(set -> IntStream.of(set).anyMatch(item -> item == doc))
            .count();
    }
}
