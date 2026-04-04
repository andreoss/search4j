/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of OR position merging.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.inmemory.InMemoryShard;
import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.backend.list.VectorPositionList;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Position union vectors and use inside phrase queries.
 *
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.UnitTestContainsTooManyAsserts",
    "PMD.UnnecessaryLocalRule"
})
final class OrPositionListTest {

    @Test
    void mergesSortedUniquePositions() {
        final PositionList merged = new OrPositionList(
            List.of(
                new VectorPositionList(
                    PositionalTestData.positions(1, 3, 7)
                ),
                new VectorPositionList(
                    PositionalTestData.positions(1, 2, 7)
                ),
                new VectorPositionList(PositionalTestData.positions(4, 7))
            )
        );
        Assertions.assertEquals(
            new TermCount(8), merged.approxSize(), "Upper size estimate"
        );
        Assertions.assertEquals(
            new TermPos(7), merged.back(), "Last merged position"
        );
        Assertions.assertEquals(
            PositionalTestData.positions(1, 2, 3, 4, 7),
            PositionalTestData.read(merged),
            "Sorted unique merge"
        );
    }

    @Test
    void skipStaysAtOrAfterTheTarget() {
        final PositionList merged = new OrPositionList(
            List.of(
                new VectorPositionList(
                    PositionalTestData.positions(1, 3, 7)
                ),
                new VectorPositionList(
                    PositionalTestData.positions(2, 4, 7)
                )
            )
        );
        Assertions.assertTrue(merged.skipTo(new TermPos(3)), "Skip succeeds");
        Assertions.assertEquals(new TermPos(3), merged.position(), "Skip target");
        Assertions.assertTrue(
            merged.skipTo(new TermPos(2)), "Backward skip is stable"
        );
        Assertions.assertEquals(
            new TermPos(3), merged.position(), "No backward movement"
        );
    }

    @Test
    void suppliesOrPositionsToPhraseOperators() {
        final List<Map<String, List<Integer>>> documents = List.of(
            Map.of(
                "a", List.of(0, 2),
                "b", List.of(1, 3),
                "c", List.of(3)
            )
        );
        final InMemoryShard database = PositionalTestData.shard(documents);
        final PostList disjunction = new OrPostList(
            database.openPostList("a"),
            database.openPostList("b"),
            () -> { }
        );
        final OrPosPostList wrapped = new OrPosPostList(disjunction);
        wrapped.skipTo(DocId.of(1));
        Assertions.assertEquals(
            PositionalTestData.positions(0, 1, 2, 3),
            PositionalTestData.read(wrapped.readPositionList()),
            "OR wrapper merges positions from the current document"
        );
        Assertions.assertEquals(
            String.format(
                "OrPosPostList(OrPostList(%s, %s))",
                "InMemoryPostList(a)", "InMemoryPostList(b)"
            ),
            wrapped.description(),
            "OR position description"
        );
        final PostList alternative = new OrPosPostList(
            new OrPostList(
                database.openPostList("a"),
                database.openPostList("b"),
                () -> { }
            )
        );
        final PostList following = database.openPostList("c");
        final List<PostList> terms = List.of(alternative, following);
        Assertions.assertEquals(
            List.of(DocId.of(1)),
            PositionalTestData.drain(
                new PhrasePostList(
                    new AndPostList(terms, () -> { }),
                    new TermPos(2), terms
                )
            ),
            "OR positions participate in a phrase"
        );
    }
}
