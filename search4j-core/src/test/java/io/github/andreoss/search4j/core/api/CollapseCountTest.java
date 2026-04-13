/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.backend.inmemory.InMemoryShard;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks collapse counts, collapse keys and the bounds before collapsing
 * over the shared corpus, with and without a filter accepting odd documents,
 * against results captured from the reference library.
 *
 * <p>Each result lists the matches as document, collapse count and key,
 * then the bounds after and before collapsing. Keys group documents in
 * threes, or are one fixed key. A last case has a left-out match weighing
 * exactly the minimum weight a percentage cutoff sets.</p>
 *
 * @since 0.7.0
 */
final class CollapseCountTest {

    @Test
    void matchesCapturedCountsUnderAFilter() {
        Assertions.assertEquals(
            List.of(
                "7/0/2 | 3 7 9 | 3 7 9",
                "7/0/2 9/0/2 3/0/0 1/0/0 | 4 4 4 | 4 4 5",
                "7/1/2 3/1/0 | 2 2 3 | 4 4 5",
                " | 0 9 10 | 0 9 10",
                "7/0/ | 3 7 9 | 3 7 9"
            ),
            List.of(
                CollapseCountTest.search(new Search(true, 2, 0).range(0, 1)),
                CollapseCountTest.search(new Search(true, 2, 0).range(0, 10)),
                CollapseCountTest.search(new Search(true, 1, 0).range(0, 2)),
                CollapseCountTest.search(new Search(true, 2, 0).fixed().range(0, 0)),
                CollapseCountTest.search(new Search(true, 0, 0).range(0, 1))
            ),
            "Matches with counts and keys, bounds after and before collapsing"
        );
    }

    @Test
    void matchesCapturedCountsWithoutAFilter() {
        Assertions.assertEquals(
            List.of(
                " | 2 9 10 | 5 9 10",
                "7/1/2 9/1/2 10/0/3 3/1/0 6/0/1 4/0/1 1/1/0 | 7 7 7 | 7 9 10",
                "7/2/2 10/0/3 3/2/0 | 4 4 5 | 5 9 10",
                "3/2/0 6/1/1 | 4 4 4 | 5 9 10",
                " | 5 9 10 | 5 9 10"
            ),
            List.of(
                CollapseCountTest.search(new Search(false, 2, 0).fixed().range(0, 0)),
                CollapseCountTest.search(new Search(false, 2, 0).range(0, 10)),
                CollapseCountTest.search(new Search(false, 1, 0).range(0, 3)),
                CollapseCountTest.search(new Search(false, 1, 0).range(2, 3)),
                CollapseCountTest.search(new Search(false, 0, 0).range(0, 0))
            ),
            "Matches with counts and keys, bounds after and before collapsing"
        );
    }

    @Test
    void matchesCapturedCountsUnderAPercentCutoff() {
        Assertions.assertEquals(
            List.of(
                "7/1/2 10/0/3 | 2 2 6 | 2 5 10",
                "7/1/2 10/0/3 3/1/0 6/1/1 | 4 4 4 | 5 9 10"
            ),
            List.of(
                CollapseCountTest.search(new Search(false, 1, 50).range(0, 2)),
                CollapseCountTest.search(new Search(false, 1, 30).range(0, 10))
            ),
            "Counts at most one once a percentage cutoff applies"
        );
    }

    @Test
    void countsDuplicateWeighingExactlyTheFinalMinimum() {
        Assertions.assertEquals(
            List.of(
                "6/1/y 5/0/x 1/1/k", "6/1/y 5/0/x 3/1/k",
                "6/1/y 5/0/x 1/1/k", "6/1/y 5/0/x 3/1/k"
            ),
            List.of(
                CollapseCountTest.tie(true, 10), CollapseCountTest.tie(false, 10),
                CollapseCountTest.tie(true, 3), CollapseCountTest.tie(false, 3)
            ),
            "A left-out match weighing the minimum still counts as one"
        );
    }

    private static String tie(final boolean ascending, final int size) {
        final InMemoryShard shard = new InMemoryShard();
        for (int docid = 1; docid <= 6; docid = docid + 1) {
            final DocumentInternal doc = new DocumentInternal();
            if (docid <= 3) {
                doc.addTerm("a", TermCount.ONE);
                doc.addTerm("b", TermCount.ONE);
            } else {
                doc.addTerm("a", new TermCount(docid));
                doc.addTerm("c", TermCount.ONE);
            }
            shard.replaceDocument(DocId.of(docid), doc);
        }
        final Enquire enquire = new EnquireImpl(shard);
        enquire.setQuery(MatcherVectors.parse("a"));
        enquire.setKeyMaker(docid -> CollapseCountTest.key(docid.value()));
        enquire.setCollapseMax(1);
        enquire.setCutoff(10);
        enquire.setDocIdOrder(ascending);
        final List<String> items = new ArrayList<>(3);
        for (final MSet.Item item : enquire.getMSet(0, size)) {
            items.add(
                String.format(
                    "%d/%d/%s", item.docId().value(), item.collapseCount().value(),
                    item.collapseKey()
                )
            );
        }
        return String.join(" ", items);
    }

    private static String key(final int docid) {
        final String result;
        if (docid <= 3) {
            result = "k";
        } else if (docid % 2 == 1) {
            result = "x";
        } else {
            result = "y";
        }
        return result;
    }

    private static String search(final Search search) {
        final Enquire enquire = new EnquireImpl(WeightFixture.valued("main"));
        enquire.setQuery(
            MatcherVectors.parse("(OR alpha beta gamma delta epsilon zeta)")
        );
        enquire.setCutoff(search.percent());
        if (search.max() > 0) {
            if (search.single()) {
                enquire.setKeyMaker(docid -> "k");
            } else {
                enquire.setKeyMaker(
                    docid -> Integer.toString((docid.value() - 1) / 3)
                );
            }
            enquire.setCollapseMax(search.max());
        }
        final MSet found;
        if (search.filter()) {
            found = enquire.getMSet(
                search.first(), search.size(), docid -> docid.value() % 2 == 1
            );
        } else {
            found = enquire.getMSet(search.first(), search.size());
        }
        final List<String> items = new ArrayList<>(8);
        for (final MSet.Item item : found) {
            items.add(
                String.format(
                    "%d/%d/%s", item.docId().value(), item.collapseCount().value(),
                    item.collapseKey()
                )
            );
        }
        return String.format(
            "%s | %d %d %d | %d %d %d", String.join(" ", items),
            found.matchesLowerBound().value(), found.matchesEstimated().value(),
            found.matchesUpperBound().value(),
            found.uncollapsedMatchesLowerBound().value(),
            found.uncollapsedMatchesEstimated().value(),
            found.uncollapsedMatchesUpperBound().value()
        );
    }

    /**
     * Settings of a search.
     *
     * @param filter Whether only odd documents are accepted
     * @param max Matches kept per key, zero for no collapsing
     * @param percent Percentage cutoff
     * @param single Whether every document has one fixed key
     * @param first Number of best matches skipped
     * @param size Number of matches wanted
     * @since 0.7.0
     */
    private record Search(
        boolean filter, int max, int percent, boolean single, int first, int size
    ) {

        /**
         * Creates settings keyed in threes, for the first ten matches.
         *
         * @param filter Whether only odd documents are accepted
         * @param max Matches kept per key, zero for no collapsing
         * @param percent Percentage cutoff
         */
        Search(final boolean filter, final int max, final int percent) {
            this(filter, max, percent, false, 0, 10);
        }

        Search fixed() {
            return new Search(
                this.filter, this.max, this.percent, true, this.first, this.size
            );
        }

        Search range(final int from, final int count) {
            return new Search(
                this.filter, this.max, this.percent, this.single, from, count
            );
        }
    }
}
