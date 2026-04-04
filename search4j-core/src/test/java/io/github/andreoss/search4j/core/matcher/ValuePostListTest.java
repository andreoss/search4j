/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of value based posting list operators.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.backend.inmemory.InMemoryShard;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Value range and value lower bound selection.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
final class ValuePostListTest {

    @Test
    void selectsValuesInsideTheRange() {
        final InMemoryShard shard = ValuePostListTest.populated();
        Assertions.assertEquals(
            List.of(DocId.of(2), DocId.of(4)),
            ValuePostListTest.drain(
                new ValueRangePostList(
                    shard, new ValueNo(1), "b", "delta"
                )
            ),
            "Inclusive bounds on both ends"
        );
        Assertions.assertEquals(
            List.of(DocId.of(2)),
            ValuePostListTest.drain(
                new ValueRangePostList(
                    shard, new ValueNo(1), "b", "b"
                )
            ),
            "Single valued range"
        );
        Assertions.assertEquals(
            List.of(),
            ValuePostListTest.drain(
                new ValueRangePostList(
                    shard, new ValueNo(1), "z", "zz"
                )
            ),
            "Range entirely above every value"
        );
        Assertions.assertEquals(
            List.of(DocId.of(1), DocId.of(2), DocId.of(4)),
            ValuePostListTest.drain(
                new ValueRangePostList(
                    shard, new ValueNo(1), "", "\uffff"
                )
            ),
            "Wide range includes every stored value"
        );
    }

    @Test
    void selectsValuesAtOrAboveTheBound() {
        final InMemoryShard shard = ValuePostListTest.populated();
        Assertions.assertEquals(
            List.of(DocId.of(2), DocId.of(4)),
            ValuePostListTest.drain(
                new ValueGePostList(shard, new ValueNo(1), "b")
            ),
            "Lower bound is inclusive"
        );
        Assertions.assertEquals(
            List.of(DocId.of(4)),
            ValuePostListTest.drain(
                new ValueGePostList(shard, new ValueNo(1), "c")
            ),
            "Values below the bound are rejected"
        );
        Assertions.assertEquals(
            List.of(),
            ValuePostListTest.drain(
                new ValueGePostList(shard, new ValueNo(1), "z")
            ),
            "Every value below the bound"
        );
    }

    @Test
    void reportsFrequencyWeightsAndDescriptions() {
        final InMemoryShard shard = ValuePostListTest.populated();
        final PostList range = new ValueRangePostList(
            shard, new ValueNo(1), "a", "z"
        );
        Assertions.assertEquals(
            3, range.termFrequency().value(), "Slot frequency"
        );
        Assertions.assertEquals(
            0.0, range.recalcMaxWeight(), 1.0e-9, "No weight"
        );
        Assertions.assertEquals(
            "ValueRangePostList(1, a, z)", range.description(), "Description"
        );
        Assertions.assertEquals(
            "ValueGePostList(1, c)",
            new ValueGePostList(shard, new ValueNo(1), "c").description(),
            "Lower bound description"
        );
    }

    @Test
    void skipsAndChecksWithinTheSlot() {
        final InMemoryShard shard = ValuePostListTest.populated();
        final PostList list = new ValueGePostList(
            shard, new ValueNo(1), "a"
        );
        Assertions.assertEquals(
            DocId.of(2),
            list.skipTo(DocId.of(2)).docId(),
            "Skip to a candidate"
        );
        Assertions.assertEquals(
            DocId.of(4),
            list.skipTo(DocId.of(4)).docId(),
            "Skip past rejected values"
        );
        Assertions.assertTrue(
            new ValueGePostList(shard, new ValueNo(1), "c")
                .check(DocId.of(4), 0.0).valid(),
            "Check accepts a value at or above the bound"
        );
        Assertions.assertFalse(
            new ValueGePostList(shard, new ValueNo(1), "a")
                .check(DocId.of(3), 0.0).valid(),
            "Check rejects a document without the slot"
        );
    }

    @Test
    void rejectsInvalidConstruction() {
        final InMemoryShard shard = ValuePostListTest.populated();
        Assertions.assertThrows(
            NullPointerException.class,
            () -> new ValueRangePostList(null, new ValueNo(1), "a", "b"),
            "Shard is required"
        );
        Assertions.assertThrows(
            NullPointerException.class,
            () -> new ValueGePostList(shard, null, "a"),
            "Slot is required"
        );
        Assertions.assertThrows(
            NullPointerException.class,
            () -> new ValueGePostList(shard, new ValueNo(1), null),
            "Bound is required"
        );
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> new ValueRangePostList(
                shard, new ValueNo(1), "z", "a"
            ),
            "Lower bound above the upper bound"
        );
    }

    private static InMemoryShard populated() {
        final InMemoryShard shard = new InMemoryShard();
        shard.addDocument(ValuePostListTest.document("alpha", "a"));
        shard.addDocument(ValuePostListTest.document("bravo", "b"));
        shard.addDocument(ValuePostListTest.document("charlie", ""));
        shard.addDocument(ValuePostListTest.document("delta", "delta"));
        return shard;
    }

    private static DocumentInternal document(
        final String term, final String value
    ) {
        final DocumentInternal doc = new DocumentInternal();
        doc.setData(term);
        doc.addTerm(term, new TermCount(1));
        if (!value.isEmpty()) {
            doc.addValue(new ValueNo(1), value);
        }
        return doc;
    }

    private static List<DocId> drain(final PostList source) {
        PostList list = source;
        final List<DocId> result = new ArrayList<>(0);
        while (!list.atEnd()) {
            list = list.next();
            if (!list.atEnd()) {
                result.add(list.docId());
            }
        }
        return result;
    }
}
