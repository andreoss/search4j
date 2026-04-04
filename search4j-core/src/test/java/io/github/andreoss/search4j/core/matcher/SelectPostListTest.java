/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of positional filter scoring and error behaviour.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.error.InvariantError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Weight preservation and invalid positional operations.
 *
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.UnitTestContainsTooManyAsserts",
    "PMD.UnnecessaryLocalRule"
})
final class SelectPostListTest {

    @Test
    void preservesWeightsFrequenciesAndDescriptions() {
        final BooleanPostListFixtureFake left = BooleanPostListFixture.fake(
            "left", 1
        ).positions(1, 4).scoring(2.0).maxed(3.0).freq(2).subs(1);
        final BooleanPostListFixtureFake right = BooleanPostListFixture.fake(
            "right", 1
        ).positions(2, 5).scoring(5.0).maxed(7.0).freq(4).subs(1);
        final List<PostList> terms = List.of(left, right);
        final PostList source = new AndPostList(terms, () -> { });
        final PhrasePostList phrase = new PhrasePostList(
            source, new TermPos(2), terms
        );
        phrase.skipTo(DocId.of(1));
        Assertions.assertEquals(
            7.0, phrase.weight(BooleanPostListFixture.doc()), 1.0e-9, "Weight"
        );
        Assertions.assertEquals(
            10.0, phrase.recalcMaxWeight(), 1.0e-9, "Maximum weight"
        );
        Assertions.assertEquals(new TermCount(2), phrase.wdf(), "Minimum term frequency");
        Assertions.assertEquals(
            new TermCount(2), phrase.countMatchingSubqueries(), "Matched leaves"
        );
        Assertions.assertEquals(
            new TermCount(0), phrase.termFrequency(), "Reference estimate"
        );
        Assertions.assertEquals(
            "(Phrase 2 (left AND right))", phrase.description(), "Phrase text"
        );
        Assertions.assertEquals(
            "(ExactPhrase (left AND right))",
            new ExactPhrasePostList(source, terms).description(),
            "Exact phrase text"
        );
        Assertions.assertEquals(
            "(Near 3 (left AND right))",
            new NearPostList(source, new TermPos(3), terms).description(),
            "Near text"
        );
    }

    @Test
    void rejectsInvalidConstructionAndPositionAccess() {
        final List<PostList> terms = List.of(
            BooleanPostListFixture.fake("a", 1),
            BooleanPostListFixture.fake("b", 1)
        );
        final PostList source = new AndPostList(terms, () -> { });
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> new PhrasePostList(
                source, new TermPos(2), List.of(terms.get(0))
            ),
            "Phrase needs at least two terms"
        );
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> new NearPostList(source, new TermPos(2), List.of()),
            "Near needs at least two terms"
        );
        Assertions.assertThrows(
            NullPointerException.class,
            () -> new ExactPhrasePostList(null, terms),
            "Source is required"
        );
        Assertions.assertThrows(
            NullPointerException.class,
            () -> new PhrasePostList(source, null, terms),
            "Window is required"
        );
        Assertions.assertThrows(
            NullPointerException.class,
            () -> new OrPositionList(null),
            "Position sources are required"
        );
        final OrPositionList empty = new OrPositionList(List.of());
        Assertions.assertThrows(
            InvalidOperationError.class,
            empty::position,
            "Position is unavailable before iteration"
        );
        Assertions.assertFalse(empty.next(), "Empty merge ends");
        Assertions.assertThrows(
            InvariantError.class,
            () -> new OrPosPostList(
                new AndNotPostList(
                    BooleanPostListFixture.fake("left", 1),
                    BooleanPostListFixture.fake("right", 2),
                    BooleanPostListFixture.size()
                )
            ).readPositionList(),
            "Unsupported position gathering preserves the invariant error"
        );
    }
}
