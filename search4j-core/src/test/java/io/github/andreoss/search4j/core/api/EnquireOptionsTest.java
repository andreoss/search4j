/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.error.UnimplementedError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the search options of an enquire over the shared corpus: default
 * scheme, filters, spies, orders, collapsing, cutoffs and bounds.
 *
 * @since 0.4.0
 */
final class EnquireOptionsTest {

    /**
     * Disjunction of six terms matching nine documents.
     */
    private static final String WIDE = "(OR alpha beta gamma delta epsilon zeta)";

    @Test
    void returnsNothingWithoutQuery() {
        Assertions.assertEquals(
            List.of(0, 0, 0, 0),
            EnquireOptionsTest.counts(
                new EnquireImpl(WeightFixture.valued("main")).getMSet(0, 10)
            ),
            "Items and bounds without a query"
        );
    }

    @Test
    void weighsWithBestMatchByDefault() {
        final Enquire plain = EnquireOptionsTest.enquire(EnquireOptionsTest.WIDE);
        final Enquire chosen = EnquireOptionsTest.enquire(EnquireOptionsTest.WIDE);
        chosen.setWeightingScheme(new BM25Weight());
        Assertions.assertEquals(
            EnquireOptionsTest.pairs(chosen.getMSet(0, 10)),
            EnquireOptionsTest.pairs(plain.getMSet(0, 10)),
            "Default scheme"
        );
    }

    @Test
    void keepsSchemeWhenQueryChanges() {
        final Enquire enquire = new EnquireImpl(WeightFixture.valued("main"));
        enquire.setWeightingScheme(new BoolWeight());
        enquire.setQuery(MatcherVectors.parse("(OR heavy rare)"));
        Assertions.assertEquals(
            "2:0.0 4:0.0 6:0.0 7:0.0 8:0.0 10:0.0",
            EnquireOptionsTest.pairs(enquire.getMSet(0, 10)),
            "Unweighted matches in document order"
        );
    }

    @Test
    void estimatesWithoutMatchingWhenNothingIsWanted() {
        final Enquire enquire = EnquireOptionsTest.enquire(EnquireOptionsTest.WIDE);
        Assertions.assertEquals(
            List.of(List.of(0, 5, 9, 10), List.of(0, 0, 9, 10)),
            List.of(
                EnquireOptionsTest.counts(enquire.getMSet(0, 0)),
                EnquireOptionsTest.counts(
                    enquire.getMSet(0, 0, docid -> true)
                )
            ),
            "Bounds before matching, without and with a filter"
        );
    }

    @Test
    void clampsCollapsedLowerBoundWhenNothingIsWanted() {
        final Enquire enquire = EnquireOptionsTest.enquire(EnquireOptionsTest.WIDE);
        enquire.setKeyMaker(docid -> "k");
        enquire.setCollapseMax(2);
        Assertions.assertEquals(
            List.of(0, 2, 9, 10),
            EnquireOptionsTest.counts(enquire.getMSet(0, 0)),
            "Lower bound clamped to the group size"
        );
    }

    @Test
    void filtersCandidates() {
        final Enquire enquire = EnquireOptionsTest.enquire(EnquireOptionsTest.WIDE);
        final MSet found = enquire.getMSet(
            0, 10, docid -> docid.value() % 2 == 1
        );
        Assertions.assertEquals(
            List.of("7 9 3 1", List.of(4, 4, 4, 4)),
            List.of(
                EnquireOptionsTest.ids(found), EnquireOptionsTest.counts(found)
            ),
            "Odd documents only, with exact bounds"
        );
    }

    @Test
    void boundsFilteredMatchesWhenFull() {
        final Enquire enquire = EnquireOptionsTest.enquire(EnquireOptionsTest.WIDE);
        final MSet found = enquire.getMSet(
            0, 1, docid -> docid.value() % 2 == 1
        );
        Assertions.assertEquals(
            List.of("7", List.of(1, 3, 7, 9)),
            List.of(
                EnquireOptionsTest.ids(found), EnquireOptionsTest.counts(found)
            ),
            "Bounds scaled by the documents the filter accepted"
        );
    }

    @Test
    void reportsEveryCountedCandidateToSpy() {
        final Enquire enquire = EnquireOptionsTest.enquire(EnquireOptionsTest.WIDE);
        final List<String> seen = new ArrayList<>(16);
        enquire.setMatchSpy(
            new MatchSpy() {
                @Override
                public void add(
                    final DocId docid, final double weight, final TermCount wdf
                ) {
                    seen.add(String.format("%d:%d", docid.value(), wdf.value()));
                }

                @Override
                public Object result() {
                    return seen;
                }
            }
        );
        enquire.getMSet(0, 10);
        Assertions.assertEquals(
            "[1:4, 2:3, 3:6, 4:5, 6:7, 7:5, 8:2, 9:7, 10:8]",
            seen.toString(),
            "Documents and frequencies seen"
        );
    }

    @Test
    void ordersByDocumentWhenAsked() {
        final Enquire enquire = EnquireOptionsTest.enquire(EnquireOptionsTest.WIDE);
        enquire.setDocIdOrder(true);
        Assertions.assertEquals(
            "2 3 4", EnquireOptionsTest.ids(enquire.getMSet(1, 3)),
            "Ascending documents from the second"
        );
    }

    @Test
    void sortsByKeyBothWays() {
        final Enquire enquire = EnquireOptionsTest.enquire(EnquireOptionsTest.WIDE);
        enquire.setSortByKey(docid -> Integer.toString(docid.value() % 3), true);
        final String upward = EnquireOptionsTest.ids(enquire.getMSet(0, 4));
        enquire.setSortByKey(docid -> Integer.toString(docid.value() % 3), false);
        Assertions.assertEquals(
            List.of("3 6 9 1", "2 8 1 4"),
            List.of(upward, EnquireOptionsTest.ids(enquire.getMSet(0, 4))),
            "Keys ascending then descending, ties by document"
        );
    }

    @Test
    void sortsByValueSlot() {
        final Enquire enquire = EnquireOptionsTest.enquire(EnquireOptionsTest.WIDE);
        enquire.setSortByValue(new ValueNo(0), false);
        Assertions.assertEquals(
            "8 3 6 10 1 7",
            EnquireOptionsTest.ids(enquire.getMSet(0, 6)),
            "Values descending, documents without values last"
        );
    }

    @Test
    void refusesPercentCutoffWhenSortingByValue() {
        final Enquire enquire = EnquireOptionsTest.enquire(EnquireOptionsTest.WIDE);
        enquire.setSortByValue(new ValueNo(0), true);
        enquire.setCutoff(50);
        Assertions.assertThrows(
            UnimplementedError.class, () -> enquire.getMSet(0, 10),
            "Percentage cutoff with value order"
        );
    }

    @Test
    void stopsCountingOnceTimeRunsOut() {
        final Enquire enquire = EnquireOptionsTest.enquire(EnquireOptionsTest.WIDE);
        final List<Integer> exact = EnquireOptionsTest.counts(
            enquire.getMSet(0, 1, 10)
        );
        enquire.setTimeLimit(Duration.ofNanos(1L));
        Assertions.assertEquals(
            List.of(List.of(1, 9, 9, 9), 10),
            List.of(
                exact,
                EnquireOptionsTest.counts(enquire.getMSet(0, 1, 10)).get(3)
            ),
            "Exact bounds, then estimated ones once time ran out"
        );
    }

    @Test
    void collapsesAdjacentKeys() {
        final Enquire enquire = EnquireOptionsTest.enquire(EnquireOptionsTest.WIDE);
        enquire.setKeyMaker(docid -> Integer.toString((docid.value() - 1) / 3));
        enquire.setCollapseMax(2);
        Assertions.assertEquals(
            "7 10 4 1", EnquireOptionsTest.ids(enquire.getMSet(0, 10)),
            "One match from each run of keys"
        );
    }

    @Test
    void collapsesOnFixedKey() {
        final Enquire enquire = EnquireOptionsTest.enquire(EnquireOptionsTest.WIDE);
        enquire.setKeyMaker(docid -> "unused");
        enquire.setCollapseKey("same");
        enquire.setCollapseMax(3);
        Assertions.assertEquals(
            "1 2", EnquireOptionsTest.ids(enquire.getMSet(0, 10)),
            "The first match and its successor only"
        );
    }

    @Test
    void forgetsEverythingOnClear() {
        final Enquire enquire = EnquireOptionsTest.enquire(EnquireOptionsTest.WIDE);
        enquire.setCutoff(50, 1.0);
        enquire.clear();
        Assertions.assertEquals(
            List.of(List.of(0, 0, 0, 0), TermCount.ZERO),
            List.of(
                EnquireOptionsTest.counts(enquire.getMSet(0, 10)),
                enquire.getMatchEstimate()
            ),
            "Nothing to search after clearing"
        );
    }

    @Test
    void ranksFromTheFirstWanted() {
        final Enquire enquire = EnquireOptionsTest.enquire(EnquireOptionsTest.WIDE);
        final List<Integer> ranks = new ArrayList<>(3);
        final MSet found = enquire.getMSet(2, 3);
        for (final MSet.Item item : found) {
            ranks.add(item.rank());
        }
        Assertions.assertEquals(
            List.of(List.of(3, 4, 5), new TermCount(3), new TermCount(3)),
            List.of(ranks, found.size(), found.maxSize()),
            "Ranks and item counts"
        );
    }

    @Test
    void estimatesWithoutWeights() {
        Assertions.assertEquals(
            new TermCount(10),
            EnquireOptionsTest.enquire(EnquireOptionsTest.WIDE).getMatchEstimate(),
            "Upper bound before matching"
        );
    }

    @Test
    void expandsMatchingDocuments() {
        final Enquire enquire = EnquireOptionsTest.enquire("(AND alpha heavy)");
        final List<ESet.Item> items = enquire.getESet(3).toList();
        Assertions.assertTrue(
            items.size() == 3
                && items.get(0).weight() >= items.get(1).weight()
                && items.get(1).weight() >= items.get(2).weight(),
            "Three expansion terms, best first"
        );
    }

    @Test
    void expandsNothingWithoutQuery() {
        Assertions.assertEquals(
            0,
            new EnquireImpl(WeightFixture.valued("main")).getESet(3).toList().size(),
            "No expansion without a query"
        );
    }

    private static Enquire enquire(final String expression) {
        final Enquire enquire = new EnquireImpl(WeightFixture.valued("main"));
        enquire.setQuery(MatcherVectors.parse(expression));
        return enquire;
    }

    private static List<Integer> counts(final MSet found) {
        return List.of(
            found.toList().size(), found.matchesLowerBound().value(),
            found.matchesEstimated().value(), found.matchesUpperBound().value()
        );
    }

    private static String ids(final MSet found) {
        final List<String> ids = new ArrayList<>(8);
        for (final MSet.Item item : found) {
            ids.add(Integer.toString(item.docId().value()));
        }
        return String.join(" ", ids);
    }

    private static String pairs(final MSet found) {
        final List<String> pairs = new ArrayList<>(8);
        for (final MSet.Item item : found) {
            pairs.add(String.format("%d:%s", item.docId().value(), item.weight()));
        }
        return String.join(" ", pairs);
    }
}
