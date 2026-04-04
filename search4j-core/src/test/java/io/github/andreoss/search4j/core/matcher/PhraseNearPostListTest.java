/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of phrase and near posting list operators.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.inmemory.InMemoryShard;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.types.DocId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Positional matching checks against a brute-force model.
 *
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.UnitTestContainsTooManyAsserts",
    "PMD.UnnecessaryLocalRule"
})
final class PhraseNearPostListTest {

    @Test
    void matchesTwoThroughFiveTermsAgainstPositionalOracle() {
        final List<Map<String, List<Integer>>> documents =
            PositionalTestData.generated();
        final InMemoryShard database = PositionalTestData.shard(documents);
        for (int count = 2; count <= 5; ++count) {
            final List<String> words = PositionalTestData.words(count);
            PhraseNearPostListTest.assertExact(database, documents, words, count);
            PhraseNearPostListTest.assertWindows(
                database, documents, words, count
            );
        }
    }

    @Test
    void matchesReferenceWindowAndQueryOrderVectors() {
        final List<Map<String, List<Integer>>> documents = List.of(
            Map.of(
                "first", List.of(1),
                "second", List.of(2, 3),
                "third", List.of(10, 11, 12),
                "milk", List.of(20),
                "rare", List.of(21)
            ),
            Map.of(
                "first", List.of(1),
                "second", List.of(2),
                "milk", List.of(21),
                "rare", List.of(20)
            )
        );
        final InMemoryShard database = PositionalTestData.shard(documents);
        Assertions.assertEquals(
            List.of(),
            PositionalTestData.drain(
                PositionalTestData.phrase(
                    database, 9, "first", "second", "third"
                )
            ),
            "Phrase window excludes the reference regression span"
        );
        Assertions.assertEquals(
            List.of(DocId.of(1)),
            PositionalTestData.drain(
                PositionalTestData.phrase(
                    database, 10, "first", "second", "third"
                )
            ),
            "Phrase window includes the reference regression span"
        );
        Assertions.assertEquals(
            List.of(DocId.of(1)),
            PositionalTestData.drain(
                PositionalTestData.phrase(database, 2, "milk", "rare")
            ),
            "Reference query order milk then rare"
        );
        Assertions.assertEquals(
            List.of(DocId.of(2)),
            PositionalTestData.drain(
                PositionalTestData.phrase(database, 2, "rare", "milk")
            ),
            "Reference query order rare then milk"
        );
    }

    @Test
    void handlesRepeatedTermsBoundariesAndOverlappingMatches() {
        final List<Map<String, List<Integer>>> documents = List.of(
            Map.of(
                "a", List.of(0, 1, 4, 5),
                "b", List.of(1, 2, 5, 6),
                "c", List.of(2, 3, 6, 7),
                "start", List.of(0),
                "end", List.of(9)
            )
        );
        final InMemoryShard database = PositionalTestData.shard(documents);
        Assertions.assertEquals(
            List.of(DocId.of(1)),
            PositionalTestData.drain(
                PositionalTestData.phrase(database, 2, "a", "b")
            ),
            "Overlapping phrase occurrences"
        );
        Assertions.assertEquals(
            List.of(DocId.of(1)),
            PositionalTestData.drain(
                PositionalTestData.phrase(database, 2, "a", "a")
            ),
            "Repeated phrase terms use distinct positions"
        );
        Assertions.assertEquals(
            List.of(),
            PositionalTestData.drain(
                PositionalTestData.near(database, 1, "a", "a")
            ),
            "Repeated near terms cannot share one position"
        );
        Assertions.assertEquals(
            List.of(DocId.of(1)),
            PositionalTestData.drain(
                PositionalTestData.near(database, 2, "a", "a")
            ),
            "Repeated near terms use distinct positions"
        );
        Assertions.assertEquals(
            List.of(DocId.of(1)),
            PositionalTestData.drain(
                PositionalTestData.phrase(database, 2, "start", "a")
            ),
            "Position zero is included"
        );
        Assertions.assertEquals(
            List.of(DocId.of(1)),
            PositionalTestData.drain(
                PositionalTestData.phrase(database, 10, "a", "end")
            ),
            "Phrase includes the last position inside its window"
        );
        Assertions.assertEquals(
            List.of(DocId.of(1)),
            PositionalTestData.drain(
                PositionalTestData.near(database, 10, "a", "end")
            ),
            "Near includes the last position inside its window"
        );
    }

    private static void assertExact(
        final InMemoryShard database,
        final List<Map<String, List<Integer>>> documents,
        final List<String> words,
        final int count
    ) {
        final List<PostList> terms = PositionalTestData.terms(database, words);
        final PostList source = new AndPostList(terms, () -> { });
        Assertions.assertEquals(
            PositionalOracle.exact(documents, words),
            PositionalTestData.drain(new ExactPhrasePostList(source, terms)),
            String.format("Exact phrase of %d terms", count)
        );
    }

    private static void assertWindows(
        final InMemoryShard database,
        final List<Map<String, List<Integer>>> documents,
        final List<String> words,
        final int count
    ) {
        for (int window = 1; window <= 7; ++window) {
            PhraseNearPostListTest.assertPhrase(
                database, documents, words, count, window
            );
            PhraseNearPostListTest.assertNear(
                database, documents, words, count, window
            );
        }
    }

    private static void assertPhrase(
        final InMemoryShard database,
        final List<Map<String, List<Integer>>> documents,
        final List<String> words,
        final int count,
        final int window
    ) {
        Assertions.assertEquals(
            PositionalOracle.phrase(documents, words, window),
            PositionalTestData.drain(
                PositionalTestData.phrase(
                    database, window, words.toArray(String[]::new)
                )
            ),
            String.format("Phrase count=%d window=%d", count, window)
        );
    }

    private static void assertNear(
        final InMemoryShard database,
        final List<Map<String, List<Integer>>> documents,
        final List<String> words,
        final int count,
        final int window
    ) {
        Assertions.assertEquals(
            PositionalOracle.near(documents, words, window),
            PositionalTestData.drain(
                PositionalTestData.near(
                    database, window, words.toArray(String[]::new)
                )
            ),
            String.format("Near count=%d window=%d", count, window)
        );
    }
}
