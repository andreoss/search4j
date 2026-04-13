/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.api.query.Expansion;
import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.api.query.Wildcard;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.WildcardLimit;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Checks counts at and above two to the thirty-one against vectors captured
 * from the reference library: both backends, every weighting scheme and the
 * serialised query.
 *
 * @since 0.8.0
 */
final class CountVectorsTest {

    @Test
    void matchesReferenceDatabasesAndSearches(@TempDir final Path temp)
        throws IOException {
        final CountVectors vectors = new CountVectors(temp);
        Assertions.assertEquals(
            List.of(List.of(), 8788),
            List.of(vectors.check(), vectors.produced()),
            "Statistics, postings, term lists, weights and expansions, both backends"
        );
    }

    @Test
    void matchesPartsTheReferencePassedOverLargeCounts() throws IOException {
        final WeightVectors vectors = new WeightVectors();
        Assertions.assertEquals(
            List.of(List.of(), 9901),
            List.of(vectors.check("SMEX", "count-part-vectors.tsv"), vectors.replayed()),
            "Term and extra parts of every scheme over the large-count databases"
        );
    }

    @Test
    void matchesPartsOfStatisticsAboveTheSignedRange() throws IOException {
        final WeightVectors vectors = new WeightVectors();
        Assertions.assertEquals(
            List.of(List.of(), 2652),
            List.of(vectors.check("SMEX", "count-wide-vectors.tsv"), vectors.replayed()),
            "Term and extra parts of every scheme for every statistic above 2^31"
        );
    }

    @Test
    void serialisesQueriesAsTheReference() throws IOException {
        final Map<String, String[]> specs = new TreeMap<>();
        for (final String[] row : WeightVectors.rows("count-query-specs.tsv")) {
            specs.put(row[0], row);
        }
        final List<List<String>> expected = new ArrayList<>(12);
        final List<List<String>> actual = new ArrayList<>(12);
        for (final String[] row : WeightVectors.rows("count-query-vectors.tsv")) {
            final Query query = CountVectorsTest.query(specs.get(row[1]));
            final Query back = Query.deserialise(HexFormat.of().parseHex(row[2]));
            expected.add(List.of(row[1], row[2], row[3], row[4], row[5], row[6]));
            actual.add(
                List.of(
                    row[1], HexFormat.of().formatHex(query.serialise()),
                    query.toString(), query.length().toString(), back.toString(),
                    HexFormat.of().formatHex(back.serialise())
                )
            );
        }
        Assertions.assertEquals(
            List.of(expected, 12), List.of(actual, actual.size()),
            "Bytes, description, length and decoding of every captured query"
        );
    }

    private static Query query(final String... spec) {
        final Query result;
        if ("WILDCARD".equals(spec[1])) {
            result = Query.wildcard(
                Wildcard.of(spec[2]).withExpansion(
                    new Expansion(TermCount.parse(spec[3]), WildcardLimit.ERROR)
                )
            );
        } else if ("EMPTY".equals(spec[1])) {
            result = Query.term("", TermCount.parse(spec[2]), TermPos.parse(spec[3]));
        } else if ("POS".equals(spec[1])) {
            result = Query.term(
                spec[2], TermCount.parse(spec[3]), TermPos.parse(spec[4])
            );
        } else {
            result = CountVectors.build(spec[1], spec[2]);
        }
        return result;
    }
}
