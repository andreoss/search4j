/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.api.query.EditDistance;
import io.github.andreoss.search4j.core.api.query.Expansion;
import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.api.query.Wildcard;
import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.backend.inmemory.InMemoryShard;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.WildcardLimit;
import io.github.andreoss.search4j.core.types.WildcardPattern;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the terms matched by wildcard patterns and edit distances against
 * those captured from the reference library over one term per document.
 *
 * @since 0.10.0
 */
final class ExpansionMatchVectorsTest {

    @Test
    void matchesCapturedExpansions() throws IOException {
        final List<String[]> rows = WeightVectors.rows("expansion-match-vectors.tsv");
        final List<String> terms = List.of(
            "a", "ab", "abc", "abcd", "abd", "abxd", "acd", "ad", "b", "ba", "bab",
            "baab", "cé", "café", "cafe", "cafes", "caff", "caf€",
            "caf😀", "cxfe", "dog", "dot", "dug", "dogs", "doge", "god", "odg",
            "do", "d", "xyz", "xxyz", "zz", "aaa", "aaaa", "foxes", "fox", "fx", "f",
            "afox", "foxa", "oxf", "kfox", "Kfox", "2fox", "été", "ete"
        );
        final InMemoryShard shard = new InMemoryShard();
        for (final String term : terms) {
            final DocumentInternal doc = new DocumentInternal();
            doc.addTerm(term, TermCount.ONE);
            shard.addDocument(doc);
        }
        final List<String> failures = new ArrayList<>(0);
        for (final String[] row : rows) {
            final Query query;
            final String expected;
            if ("W".equals(row[0])) {
                query = Query.wildcard(
                    new Wildcard(
                        row[1], Expansion.UNLIMITED,
                        ExpansionMatchVectorsTest.glob(Integer.parseInt(row[2])),
                        QueryOp.OR
                    )
                );
                expected = row[3];
            } else {
                query = Query.editDistance(
                    new EditDistance(
                        row[1], new Expansion(TermCount.ZERO, WildcardLimit.ERROR),
                        QueryOp.OR, Integer.parseInt(row[2]), Integer.parseInt(row[3])
                    )
                );
                expected = row[4];
            }
            final Enquire enquire = new EnquireImpl(shard);
            enquire.setQuery(query);
            enquire.setWeightingScheme(new BoolWeight());
            final StringBuilder found = new StringBuilder(64);
            for (final MSet.Item item : enquire.getMSet(0, 1000).toList()) {
                if (found.length() > 0) {
                    found.append(',');
                }
                found.append(terms.get(item.docId().value() - 1));
            }
            if (!found.toString().equals(expected)) {
                failures.add(String.format("%s got %s", String.join(" ", row), found));
            }
        }
        Assertions.assertEquals(
            List.of(232, List.of()), List.of(rows.size(), failures),
            "Terms matched by each pattern and edit distance"
        );
    }

    private static WildcardPattern glob(final int flags) {
        WildcardPattern result = WildcardPattern.PREFIX;
        for (final WildcardPattern glob : WildcardPattern.values()) {
            if (glob.code() == flags) {
                result = glob;
            }
        }
        return result;
    }
}
