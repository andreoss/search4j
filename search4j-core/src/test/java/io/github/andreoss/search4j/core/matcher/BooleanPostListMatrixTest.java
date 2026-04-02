/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Model checks of the boolean posting list operators.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.types.DocId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Exhaustive two- and three-child streams against a set model.
 *
 * @since 0.1.0
 */
final class BooleanPostListMatrixTest {

    @Test
    void comparesEveryTwoAndThreeStreamCombinationToTheSetModel() {
        final int[][] streams = {
            {}, {1}, {2}, {1, 2}, {2, 3}, {1, 3, 4},
        };
        final List<String> faults = new ArrayList<>(0);
        for (int left = 0; left < streams.length; ++left) {
            for (int right = 0; right < streams.length; ++right) {
                BooleanPostListMatrixTest.comparePair(
                    streams, left, right, faults
                );
                BooleanPostListMatrixTest.compareThirds(
                    streams, left, right, faults
                );
            }
        }
        Assertions.assertEquals(
            List.of(), faults,
            "Every two- and three-child stream combination"
        );
    }

    private static void comparePair(
        final int[][] streams, final int left, final int right,
        final List<String> faults
    ) {
        final int[] first = streams[left];
        final int[] second = streams[right];
        BooleanPostListMatrixTest.compare(
            "AND", BooleanPostListMatrixTest.intersection(first, second),
            BooleanPostListFixture.drain(
                new AndPostList(
                    List.of(
                        BooleanPostListMatrixTest.fake(streams, left),
                        BooleanPostListMatrixTest.fake(streams, right)
                    ),
                    () -> { }
                )
            ),
            left, right, faults
        );
        BooleanPostListMatrixTest.compare(
            "OR", BooleanPostListMatrixTest.union(first, second),
            BooleanPostListFixture.drain(
                new OrPostList(
                    BooleanPostListMatrixTest.fake(streams, left),
                    BooleanPostListMatrixTest.fake(streams, right),
                    () -> { }
                )
            ),
            left, right, faults
        );
        BooleanPostListMatrixTest.compare(
            "AND_NOT", BooleanPostListMatrixTest.difference(first, second),
            BooleanPostListFixture.drain(
                new AndNotPostList(
                    BooleanPostListMatrixTest.fake(streams, left),
                    BooleanPostListMatrixTest.fake(streams, right),
                    BooleanPostListFixture.size()
                )
            ),
            left, right, faults
        );
        BooleanPostListMatrixTest.compare(
            "AND_MAYBE", BooleanPostListMatrixTest.documents(first),
            BooleanPostListFixture.drain(
                new AndMaybePostList(
                    BooleanPostListMatrixTest.fake(streams, left),
                    BooleanPostListMatrixTest.fake(streams, right),
                    () -> { }
                )
            ),
            left, right, faults
        );
        BooleanPostListMatrixTest.compare(
            "BOOL_OR", BooleanPostListMatrixTest.union(first, second),
            BooleanPostListFixture.drain(
                new BoolOrPostList(
                    List.of(
                        BooleanPostListMatrixTest.fake(streams, left),
                        BooleanPostListMatrixTest.fake(streams, right)
                    ),
                    BooleanPostListFixture.size()
                )
            ),
            left, right, faults
        );
        BooleanPostListMatrixTest.compare(
            "XOR", BooleanPostListMatrixTest.parity(first, second),
            BooleanPostListFixture.drain(
                new XorPostList(
                    List.of(
                        BooleanPostListMatrixTest.fake(streams, left),
                        BooleanPostListMatrixTest.fake(streams, right)
                    ),
                    BooleanPostListFixture.size(), () -> { }
                )
            ),
            left, right, faults
        );
    }

    private static void compareTriple(
        final int[][] streams, final int left, final int middle,
        final int right, final List<String> faults
    ) {
        final int[] first = streams[left];
        final int[] second = streams[middle];
        final int[] third = streams[right];
        BooleanPostListMatrixTest.compare(
            "AND", BooleanPostListMatrixTest.intersection(first, second, third),
            BooleanPostListFixture.drain(
                new AndPostList(
                    List.of(
                        BooleanPostListMatrixTest.fake(streams, left),
                        BooleanPostListMatrixTest.fake(streams, middle),
                        BooleanPostListMatrixTest.fake(streams, right)
                    ),
                    () -> { }
                )
            ),
            left, middle, faults
        );
        BooleanPostListMatrixTest.compare(
            "OR", BooleanPostListMatrixTest.union(first, second, third),
            BooleanPostListFixture.drain(
                new OrPostList(
                    new OrPostList(
                        BooleanPostListMatrixTest.fake(streams, left),
                        BooleanPostListMatrixTest.fake(streams, middle),
                        () -> { }
                    ),
                    BooleanPostListMatrixTest.fake(streams, right),
                    () -> { }
                )
            ),
            left, middle, faults
        );
        BooleanPostListMatrixTest.compare(
            "AND_NOT",
            BooleanPostListMatrixTest.difference(first, second, third),
            BooleanPostListFixture.drain(
                new AndNotPostList(
                    BooleanPostListMatrixTest.fake(streams, left),
                    new OrPostList(
                        BooleanPostListMatrixTest.fake(streams, middle),
                        BooleanPostListMatrixTest.fake(streams, right),
                        () -> { }
                    ),
                    BooleanPostListFixture.size()
                )
            ),
            left, middle, faults
        );
        BooleanPostListMatrixTest.compare(
            "AND_MAYBE", BooleanPostListMatrixTest.documents(first),
            BooleanPostListFixture.drain(
                new AndMaybePostList(
                    BooleanPostListMatrixTest.fake(streams, left),
                    new OrPostList(
                        BooleanPostListMatrixTest.fake(streams, middle),
                        BooleanPostListMatrixTest.fake(streams, right),
                        () -> { }
                    ),
                    () -> { }
                )
            ),
            left, middle, faults
        );
        BooleanPostListMatrixTest.compare(
            "BOOL_OR", BooleanPostListMatrixTest.union(first, second, third),
            BooleanPostListFixture.drain(
                new BoolOrPostList(
                    List.of(
                        BooleanPostListMatrixTest.fake(streams, left),
                        BooleanPostListMatrixTest.fake(streams, middle),
                        BooleanPostListMatrixTest.fake(streams, right)
                    ),
                    BooleanPostListFixture.size()
                )
            ),
            left, middle, faults
        );
        BooleanPostListMatrixTest.compare(
            "XOR", BooleanPostListMatrixTest.parity(first, second, third),
            BooleanPostListFixture.drain(
                new XorPostList(
                    List.of(
                        BooleanPostListMatrixTest.fake(streams, left),
                        BooleanPostListMatrixTest.fake(streams, middle),
                        BooleanPostListMatrixTest.fake(streams, right)
                    ),
                    BooleanPostListFixture.size(), () -> { }
                )
            ),
            left, middle, faults
        );
    }

    private static void compareThirds(
        final int[][] streams, final int left, final int middle,
        final List<String> faults
    ) {
        for (int third = 0; third < streams.length; ++third) {
            BooleanPostListMatrixTest.compareTriple(
                streams, left, middle, third, faults
            );
        }
    }

    private static List<DocId> intersection(final int[]... streams) {
        return BooleanPostListMatrixTest.select(streams, streams.length, false);
    }

    private static List<DocId> union(final int[]... streams) {
        return BooleanPostListMatrixTest.select(streams, 1, false);
    }

    private static List<DocId> parity(final int[]... streams) {
        return BooleanPostListMatrixTest.select(streams, 1, true);
    }

    private static List<DocId> select(
        final int[][] streams, final int threshold, final boolean odd
    ) {
        final List<DocId> result = new ArrayList<>(0);
        for (int doc = 1; doc <= 4; ++doc) {
            int matches = 0;
            for (final int[] stream : streams) {
                if (BooleanPostListMatrixTest.contains(stream, doc)) {
                    matches += 1;
                }
            }
            if (odd && matches % 2 == 1 || !odd && matches >= threshold) {
                result.add(DocId.of(doc));
            }
        }
        return result;
    }

    private static List<DocId> difference(
        final int[] left, final int[]... excluded
    ) {
        final List<DocId> result = new ArrayList<>(0);
        for (final int doc : left) {
            boolean blocked = false;
            for (final int[] stream : excluded) {
                blocked |= BooleanPostListMatrixTest.contains(stream, doc);
            }
            if (!blocked) {
                result.add(DocId.of(doc));
            }
        }
        return result;
    }

    private static List<DocId> documents(final int... stream) {
        final List<DocId> result = new ArrayList<>(stream.length);
        for (final int doc : stream) {
            result.add(DocId.of(doc));
        }
        return result;
    }

    private static boolean contains(final int[] stream, final int doc) {
        boolean found = false;
        for (final int value : stream) {
            if (value == doc) {
                found = true;
            }
        }
        return found;
    }

    private static void compare(
        final String name, final List<DocId> expected,
        final List<DocId> actual, final int first, final int second,
        final List<String> faults
    ) {
        if (!expected.equals(actual)) {
            faults.add(
                String.format(
                    "%s streams %d/%d expected %s but got %s",
                    name, first, second, expected, actual
                )
            );
        }
    }

    private static BooleanPostListFixtureFake fake(
        final int[][] streams, final int index
    ) {
        return BooleanPostListFixture.fake(
            String.format("stream-%d", index), streams[index]
        );
    }
}
