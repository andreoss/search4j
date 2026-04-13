/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the remaining public API types.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.backend.Shard;
import io.github.andreoss.search4j.core.backend.inmemory.InMemoryShard;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of the registry, relevance set, term info, small vector, vector term
 * list, range processor and value set decider.
 *
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.UnitTestContainsTooManyAsserts",
    "PMD.UnnecessaryLocalRule"
})
final class RemainingApiTest {

    @Test
    void describesTermStatistics() {
        Assertions.assertEquals(
            "TermInfo(termfreq=3, collfreq=7, wdf_upper_bound=2)",
            new TermInfo(
                new TermCount(3), new TermCount(7), new TermCount(2)
            ).description(),
            "Description"
        );
    }

    @Test
    void growsASmallVector() {
        final SmallVector vector = new SmallVector();
        for (int idx = 0; idx < 10; idx = idx + 1) {
            vector.add(idx * 2);
        }
        Assertions.assertEquals(
            List.of(10, 0, 4, 18),
            List.of(
                vector.size(), vector.get(0), vector.get(2), vector.get(9)
            ),
            "Size and items after growth"
        );
    }

    @Test
    void replacesAndClearsASmallVector() {
        final SmallVector vector = new SmallVector();
        vector.add(3);
        vector.set(0, 8);
        Assertions.assertEquals(8, vector.get(0), "Replaced item");
        vector.clear();
        Assertions.assertEquals(0, vector.size(), "Cleared size");
    }

    @Test
    void rejectsASmallVectorIndexOutOfRange() {
        final SmallVector vector = new SmallVector();
        Assertions.assertThrows(
            IndexOutOfBoundsException.class, () -> vector.get(0),
            "Empty vector"
        );
    }

    @Test
    void copiesASmallVectorToAnArray() {
        final SmallVector vector = new SmallVector();
        vector.add(1);
        vector.add(2);
        Assertions.assertArrayEquals(
            new int[]{1, 2}, vector.toArray(), "Items in order"
        );
    }

    @Test
    void keepsARelevanceSetInOrder() {
        final RSet set = new RSet();
        set.add(DocId.of(3));
        set.add(DocId.of(1));
        set.add(DocId.of(3));
        Assertions.assertEquals(
            List.of(DocId.of(1), DocId.of(3)), set.docIds(), "Ascending"
        );
        Assertions.assertEquals(2, set.size(), "Unique documents");
    }

    @Test
    void removesFromARelevanceSet() {
        final RSet set = new RSet();
        set.add(DocId.of(2));
        set.remove(DocId.of(2));
        Assertions.assertTrue(set.isEmpty(), "Emptied");
        Assertions.assertFalse(set.contains(DocId.of(2)), "Removed");
    }

    @Test
    void describesARelevanceSet() {
        final RSet set = new RSet();
        set.add(DocId.of(1));
        Assertions.assertEquals(
            "RSet(1 documents)", set.description(), "Description"
        );
    }

    @Test
    void iteratesAVectorTermList() {
        final VectorTermList list = new VectorTermList(
            List.of("alpha", "bravo")
        );
        Assertions.assertEquals(
            List.of("alpha", "bravo"), List.of(list.next(), list.next()),
            "Terms in order"
        );
        Assertions.assertFalse(list.hasNext(), "Exhausted");
    }

    @Test
    void reportsTheFrequencyOfAVectorTerm() {
        final VectorTermList list = new VectorTermList(List.of("alpha"));
        list.next();
        Assertions.assertEquals(TermCount.ONE, list.wdf(), "Frequency");
    }

    @Test
    void skipsTermsOfAVectorTermList() {
        final VectorTermList list = new VectorTermList(
            List.of("alpha", "bravo", "charlie")
        );
        list.skipTo("bravo");
        Assertions.assertEquals("bravo", list.next(), "Skipped to bravo");
    }

    @Test
    void rejectsAPositionRequestFromAVectorTermList() {
        final VectorTermList list = new VectorTermList(List.of("alpha"));
        list.next();
        Assertions.assertThrows(
            InvalidOperationError.class, list::positions, "No positions"
        );
    }

    @Test
    void rejectsReadingPastAVectorTermList() {
        final VectorTermList list = new VectorTermList(List.of("alpha"));
        list.next();
        Assertions.assertThrows(
            InvalidOperationError.class, list::next, "Exhausted"
        );
    }

    @Test
    void buildsAValueRangeQuery() {
        final ValueRangeProcessor processor =
            new ValueRangeProcessor(new ValueNo(2));
        Assertions.assertEquals(
            QueryOp.VALUE_RANGE, processor.process("a", "z").type(),
            "Range query"
        );
        Assertions.assertEquals(new ValueNo(2), processor.slot(), "Slot");
    }

    @Test
    void treatsAbsentBoundsAsEmpty() {
        final Query query = new ValueRangeProcessor(new ValueNo(0))
            .process(null, null);
        Assertions.assertEquals(
            QueryOp.VALUE_LE, query.type(), "Open lower bound"
        );
    }

    @Test
    void decidersAcceptDocumentsWhoseValueIsInTheSet() {
        final MatchDecider decider = new ValueSetMatchDecider(
            RemainingApiTest.shard(), new ValueNo(1), Set.of("b")
        );
        Assertions.assertEquals(
            List.of(false, true),
            List.of(decider.decide(DocId.of(1)), decider.decide(DocId.of(2))),
            "Membership"
        );
    }

    @Test
    void decidersCanReverseTheMembership() {
        final MatchDecider decider = new ValueSetMatchDecider(
            RemainingApiTest.shard(), new ValueNo(1), Set.of("b"), false
        );
        Assertions.assertTrue(
            decider.decide(DocId.of(1)), "Not a member"
        );
    }

    @Test
    void registersTheDefaultWeightingSchemes() {
        final Registry registry = new Registry();
        Assertions.assertFalse(
            registry.hasWeightingScheme("BM25"), "Names match exactly"
        );
        Assertions.assertTrue(
            registry.getWeightingScheme("bm25") instanceof BM25Weight,
            "Fresh scheme"
        );
    }

    @Test
    void listsTheWeightingSchemesInOrder() {
        Assertions.assertEquals(
            "bb2", new Registry().weightingSchemes().get(0),
            "Ascending order"
        );
    }

    @Test
    void rejectsAnUnknownWeightingScheme() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> new Registry().getWeightingScheme("missing"),
            "Unknown scheme"
        );
    }

    @Test
    void registersACustomWeightingScheme() {
        final Registry registry = new Registry();
        registry.registerWeightingScheme("custom", BoolWeight::new);
        Assertions.assertTrue(
            registry.hasWeightingScheme("custom"), "Registered"
        );
    }

    @Test
    void registersAndFindsARangeProcessor() {
        final Registry registry = new Registry();
        registry.registerRangeProcessor(
            "slot", () -> new ValueRangeProcessor(new ValueNo(1))
        );
        Assertions.assertEquals(
            List.of(true, false),
            List.of(registry.hasRangeProcessor("slot"), registry.hasRangeProcessor("SLOT")),
            "Registered under its exact name"
        );
        Assertions.assertEquals(
            new ValueNo(1),
            ((ValueRangeProcessor) registry.getRangeProcessor("slot"))
                .slot(),
            "Processor slot"
        );
    }

    @Test
    void rejectsAnUnknownRangeProcessor() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> new Registry().getRangeProcessor("missing"),
            "Unknown processor"
        );
    }

    private static Shard shard() {
        final InMemoryShard shard = new InMemoryShard();
        shard.addDocument(RemainingApiTest.document("a", "a"));
        shard.addDocument(RemainingApiTest.document("b", "b"));
        return shard;
    }

    private static DocumentInternal document(
        final String term, final String value
    ) {
        final DocumentInternal doc = new DocumentInternal();
        doc.addTerm(term, new TermCount(1));
        doc.addValue(new ValueNo(1), value);
        return doc;
    }
}
