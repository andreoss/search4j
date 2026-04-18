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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Positional matching checks against captured reference selections.
 *
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.UnitTestContainsTooManyAsserts",
    "PMD.UnnecessaryLocalRule"
})
final class PhraseNearPostListTest {

    @Test
    void matchesCapturedPositionalFiltersOfTwoThroughFiveTerms() throws IOException {
        final InMemoryShard database = PositionalTestData.shard(
            PositionalTestData.generated()
        );
        final List<String> failures = new ArrayList<>(0);
        for (final String line : PhraseNearPostListTest.lines()) {
            final String[] row = line.split("\t", -1);
            final int count = Integer.parseInt(row[1]);
            final int window = Integer.parseInt(row[2]);
            final String[] words = PositionalTestData.words(count).toArray(String[]::new);
            final PostList filter;
            if ("exact".equals(row[0])) {
                final List<PostList> terms = PositionalTestData.terms(
                    database, List.of(words)
                );
                filter = new ExactPhrasePostList(new AndPostList(terms, () -> { }), terms);
            } else if ("phrase".equals(row[0])) {
                filter = PositionalTestData.phrase(
                    database, window, words
                );
            } else {
                filter = PositionalTestData.near(database, window, words);
            }
            final StringBuilder found = new StringBuilder(64);
            for (final DocId docid : PositionalTestData.drain(filter)) {
                if (found.length() > 0) {
                    found.append(',');
                }
                found.append(docid.value());
            }
            if (!found.toString().equals(row[3])) {
                failures.add(String.format("%s got %s", line, found));
            }
        }
        Assertions.assertEquals(
            List.of(), failures, "Documents selected by the captured filters"
        );
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

    private static List<String> lines() throws IOException {
        try (
            BufferedReader input = new BufferedReader(
                new InputStreamReader(
                    PhraseNearPostListTest.class.getResourceAsStream(
                        "positional-filter-vectors.tsv"
                    ),
                    StandardCharsets.UTF_8
                )
            )
        ) {
            return input.lines().toList();
        }
    }
}
