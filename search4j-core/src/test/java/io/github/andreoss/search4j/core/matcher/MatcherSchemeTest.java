/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the matcher.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.api.BM25Weight;
import io.github.andreoss.search4j.core.api.BoolWeight;
import io.github.andreoss.search4j.core.api.Weight;
import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.backend.inmemory.InMemoryShard;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The boolean scheme leaves term lists without a weight, and an upper
 * value bound below every stored value matches nothing.
 *
 * @since 0.5.0
 */
final class MatcherSchemeTest {

    @Test
    void attachesNoWeightForTheBooleanScheme() {
        Assertions.assertEquals(
            List.of(List.of(0L, false), List.of(1L, true)),
            List.of(
                MatcherSchemeTest.leaf(new BoolWeight()),
                MatcherSchemeTest.leaf(new BM25Weight())
            ),
            "Weighted subqueries and maximum weight"
        );
    }

    @Test
    void matchesNothingBelowAnEmptyUpperBound() {
        Assertions.assertEquals(
            List.of(0, 2),
            List.of(
                MatcherSchemeTest.count(
                    Query.value(QueryOp.VALUE_LE, new ValueNo(1), "")
                ),
                MatcherSchemeTest.count(
                    Query.value(QueryOp.VALUE_LE, new ValueNo(1), "z")
                )
            ),
            "Documents with a value up to the bound"
        );
    }

    private static List<Object> leaf(final Weight weight) {
        final PostList list = Matcher.build(
            MatcherSchemeTest.shard(), Query.term("a"), weight
        ).list();
        return List.of(
            list.countMatchingSubqueries().value(), list.recalcMaxWeight() > 0.0
        );
    }

    private static int count(final Query query) {
        PostList list = Matcher.resolve(MatcherSchemeTest.shard(), query).next();
        int found = 0;
        while (!list.atEnd()) {
            found = found + 1;
            list = list.next();
        }
        return found;
    }

    private static InMemoryShard shard() {
        final InMemoryShard shard = new InMemoryShard();
        shard.addDocument(MatcherSchemeTest.document("a", "m"));
        shard.addDocument(MatcherSchemeTest.document("b", "n"));
        shard.addDocument(MatcherSchemeTest.document("c", "zz"));
        return shard;
    }

    private static DocumentInternal document(final String term, final String value) {
        final DocumentInternal doc = new DocumentInternal();
        doc.addTerm(term, new TermCount(1));
        doc.addValue(new ValueNo(1), value);
        return doc;
    }
}
