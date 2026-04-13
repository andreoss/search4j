/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.backend.inmemory.InMemoryShard;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks collapsing once a percentage cutoff drops kept matches while more
 * matches are still to be counted: documents, collapse counts and keys, and
 * the bounds after and before collapsing, against results captured from the
 * reference library.
 *
 * <p>Documents 1 and 3 have no key, document 4 has key k, and document 2
 * has key k or j. Document 3 outweighs documents 1 and 2 enough for the
 * cutoff to drop both; document 4 weighs as much as document 3. When
 * document 2 shares the key of document 4 and a key keeps one match, the
 * group of k still points at the position document 2 held: document 4 is
 * compared with the dropped document 2, takes its position past the end
 * of the kept matches, and is lost.</p>
 *
 * @since 0.5.4
 */
final class CollapseDroppedTest {

    @Test
    void losesMatchSharingTheKeyOfADroppedMatch() {
        Assertions.assertEquals(
            List.of(
                "3/0/ | 1 1 1 | 4 4 4", "3/0/ 4/0/k | 2 2 4 | 2 2 4",
                "3/0/ 4/0/k | 2 2 4 | 2 2 4"
            ),
            List.of(
                CollapseDroppedTest.search("k", 10, 1, 50),
                CollapseDroppedTest.search("k", 10, 2, 50),
                CollapseDroppedTest.search("j", 10, 1, 50)
            ),
            "Document 4 lost only when its key is that of the dropped document"
        );
    }

    @Test
    void matchesCapturedResultsOfEverySetting() {
        final List<String> found = new ArrayList<>(16);
        for (int code = 0; code < 16; code = code + 1) {
            final String second = List.of("k", "j").get(code / 8);
            final int check = 10 * (code / 4 % 2);
            final int max = 1 + code / 2 % 2;
            final int percent = 50 * (code % 2);
            found.add(
                String.format(
                    "%s %d %d %d: %s", second, check, max, percent,
                    CollapseDroppedTest.search(second, check, max, percent)
                )
            );
        }
        Assertions.assertEquals(
            List.of(
                "k 0 1 0: 3/0/ 4/1/k | 3 3 3 | 4 4 4", "k 0 1 50: 3/0/ | 1 1 1 | 4 4 4",
                "k 0 2 0: 3/0/ 4/0/k | 4 4 4 | 4 4 4", "k 0 2 50: 3/0/ | 1 1 1 | 4 4 4",
                "k 10 1 0: 3/0/ 4/1/k | 3 3 3 | 4 4 4", "k 10 1 50: 3/0/ | 1 1 1 | 4 4 4",
                "k 10 2 0: 3/0/ 4/0/k | 4 4 4 | 4 4 4",
                "k 10 2 50: 3/0/ 4/0/k | 2 2 4 | 2 2 4",
                "j 0 1 0: 3/0/ 4/0/k | 4 4 4 | 4 4 4", "j 0 1 50: 3/0/ | 1 1 1 | 4 4 4",
                "j 0 2 0: 3/0/ 4/0/k | 4 4 4 | 4 4 4", "j 0 2 50: 3/0/ | 1 1 1 | 4 4 4",
                "j 10 1 0: 3/0/ 4/0/k | 4 4 4 | 4 4 4",
                "j 10 1 50: 3/0/ 4/0/k | 2 2 4 | 2 2 4",
                "j 10 2 0: 3/0/ 4/0/k | 4 4 4 | 4 4 4",
                "j 10 2 50: 3/0/ 4/0/k | 2 2 4 | 2 2 4"
            ),
            found,
            "Documents and bounds by key of document 2, minimum to check, group size and cutoff"
        );
    }

    private static String search(
        final String second, final int check, final int max, final int percent
    ) {
        final InMemoryShard shard = new InMemoryShard();
        CollapseDroppedTest.add(shard, 1, "", 0);
        CollapseDroppedTest.add(shard, 2, second, 0);
        CollapseDroppedTest.add(shard, 3, "", 3);
        CollapseDroppedTest.add(shard, 4, "k", 3);
        final Enquire enquire = new EnquireImpl(shard);
        enquire.setQuery(
            Query.of(QueryOp.OR, List.of(Query.term("x"), Query.term("y")))
        );
        enquire.setCutoff(percent);
        enquire.setKeyMaker(
            docid -> shard.openDocument(docid, false)
                .flatMap(document -> document.value(new ValueNo(0))).orElse("")
        );
        enquire.setCollapseMax(max);
        final MSet found = enquire.getMSet(0, 2, check);
        final List<String> ids = new ArrayList<>(2);
        for (final MSet.Item item : found) {
            ids.add(
                String.format(
                    "%d/%d/%s", item.docId().value(), item.collapseCount().value(),
                    item.collapseKey()
                )
            );
        }
        return String.format(
            "%s | %d %d %d | %d %d %d", String.join(" ", ids),
            found.matchesLowerBound().value(), found.matchesEstimated().value(),
            found.matchesUpperBound().value(),
            found.uncollapsedMatchesLowerBound().value(),
            found.uncollapsedMatchesEstimated().value(),
            found.uncollapsedMatchesUpperBound().value()
        );
    }

    private static void add(
        final InMemoryShard shard, final int docid, final String key, final int wdf
    ) {
        final DocumentInternal doc = new DocumentInternal();
        doc.addTerm("x", TermCount.ONE);
        if (wdf > 0) {
            doc.addTerm("y", new TermCount(wdf));
        }
        if (!key.isEmpty()) {
            doc.addValue(new ValueNo(0), key);
        }
        shard.replaceDocument(DocId.of(docid), doc);
    }
}
