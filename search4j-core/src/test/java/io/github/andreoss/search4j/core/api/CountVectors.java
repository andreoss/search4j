/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.types.DatabaseAction;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Replays vectors the reference library produced over databases holding
 * counts at and above two to the thirty-one: statistics, postings, term
 * lists, searches with every weighting scheme and expansions, in memory and
 * on disk.
 *
 * @since 0.8.0
 */
final class CountVectors {

    /**
     * Term delimiter of the fixtures.
     */
    private static final Pattern SPACE = Pattern.compile(" ");

    /**
     * Directory for the databases on disk.
     */
    private final Path dir;

    /**
     * Rows produced by this project.
     */
    private final List<String[]> rows;

    /**
     * Creates a replay.
     *
     * @param dir Directory for the databases on disk
     */
    CountVectors(final Path dir) {
        this.dir = dir;
        this.rows = new ArrayList<>(16_384);
    }

    /**
     * Builds every corpus with both backends and compares the rows of each
     * search, expansion and statistics group with the reference rows.
     *
     * @return Failures, empty when every row matches
     * @throws IOException When the vectors cannot be read
     */
    List<String> check() throws IOException {
        final List<String[]> ops = WeightVectors.rows("count-corpus.tsv");
        final Set<String> corpora = new TreeSet<>();
        for (final String[] line : ops) {
            corpora.add(line[0]);
        }
        for (final String corpus : corpora) {
            final WritableDatabase mem = DatabaseFactory.inMemory();
            CountVectors.apply(mem, corpus, ops);
            new CountRows(this.rows, "inmemory").add(corpus, mem);
            final Path path = this.dir.resolve(corpus);
            try (
                WritableDatabase disk = DatabaseFactory.openWritable(
                    path, DatabaseAction.CREATE_OR_OVERWRITE.code(), 2048
                )
            ) {
                CountVectors.apply(disk, corpus, ops);
            }
            try (Database disk = DatabaseFactory.open(path)) {
                new CountRows(this.rows, "disk").add(corpus, disk);
            }
        }
        final List<String[]> expected = new ArrayList<>(
            WeightVectors.rows("count-vectors.tsv")
        );
        expected.addAll(WeightVectors.rows("count-expand-vectors.tsv"));
        return CountVectors.compare(expected, this.rows);
    }

    /**
     * Number of rows produced by the last check.
     *
     * @return Rows
     */
    int produced() {
        return this.rows.size();
    }

    /**
     * Builds a query from an operator and terms with optional frequencies.
     *
     * @param operator Operator name, TERM for a single leaf or SCALE: and a
     *  factor
     * @param terms Terms separated by spaces, each with an optional colon and
     *  within-query frequency
     * @return The query
     */
    static Query build(final String operator, final String terms) {
        final List<Query> leaves = new ArrayList<>(4);
        for (final String term : CountVectors.SPACE.split(terms, -1)) {
            final int colon = term.indexOf(':');
            if (colon < 0) {
                leaves.add(Query.term(term));
            } else {
                leaves.add(
                    Query.term(
                        term.substring(0, colon), TermCount.parse(term.substring(colon + 1))
                    )
                );
            }
        }
        final Query result;
        if ("TERM".equals(operator)) {
            result = leaves.get(0);
        } else if (operator.startsWith("SCALE:")) {
            result = Query.scale(Double.parseDouble(operator.substring(6)), leaves.get(0));
        } else {
            result = Query.of(QueryOp.valueOf(operator), leaves);
        }
        return result;
    }

    private static void apply(
        final WritableDatabase base, final String corpus, final List<String[]> ops
    ) {
        for (final String[] line : ops) {
            if (line[0].equals(corpus)) {
                final DocId doc = DocId.parse(line[2]);
                if ("delete".equals(line[1])) {
                    base.deleteDocument(doc);
                } else {
                    base.replaceDocument(doc, CountVectors.document(line[3]));
                }
            }
        }
        base.commit();
    }

    private static Document document(final String terms) {
        final Document doc = DatabaseFactory.document();
        for (final String term : CountVectors.SPACE.split(terms, -1)) {
            final int colon = term.indexOf(':');
            final int sign = term.indexOf('@');
            final TermCount wdf = TermCount.parse(term.substring(colon + 1));
            if (sign >= 0 && sign < colon) {
                doc.addPosting(
                    term.substring(0, sign),
                    TermPos.parse(term.substring(sign + 1, colon)), wdf
                );
            } else {
                doc.addTerm(term.substring(0, colon), wdf);
            }
        }
        return doc;
    }

    private static List<String> compare(
        final List<String[]> expected, final List<String[]> actual
    ) {
        final Map<String, List<String[]>> want = CountVectors.groups(expected);
        final Map<String, List<String[]>> got = CountVectors.groups(actual);
        final List<String> failures = new ArrayList<>(0);
        for (final Map.Entry<String, List<String[]>> group : want.entrySet()) {
            final List<String[]> found = got.getOrDefault(group.getKey(), List.of());
            if (!CountVectors.same(group.getValue(), found)) {
                failures.add(
                    String.format(
                        "%s: %s got %s", group.getKey(),
                        CountVectors.text(group.getValue()), CountVectors.text(found)
                    )
                );
            }
        }
        if (!want.keySet().equals(got.keySet())) {
            failures.add(String.format("groups %s got %s", want.keySet(), got.keySet()));
        }
        return failures;
    }

    private static Map<String, List<String[]>> groups(final List<String[]> rows) {
        final Map<String, List<String[]>> result = new LinkedHashMap<>(4096);
        for (final String[] row : rows) {
            final String key;
            if ("DCXZ".contains(row[0])) {
                key = String.join(" ", row[1], row[2], row[3]);
            } else {
                key = String.join(" ", row[0], row[1], row[2]);
            }
            result.computeIfAbsent(key, item -> new ArrayList<>(8)).add(row);
        }
        return result;
    }

    private static boolean same(final List<String[]> expected, final List<String[]> actual) {
        boolean result = expected.size() == actual.size();
        for (int idx = 0; result && idx < expected.size(); ++idx) {
            result = CountVectors.matches(expected.get(idx), actual.get(idx));
        }
        return result;
    }

    private static boolean matches(final String[] expected, final String... actual) {
        int real = -1;
        if ("GDX".contains(expected[0])) {
            real = 6;
        }
        boolean result = expected.length == actual.length;
        for (int idx = 0; result && idx < expected.length; ++idx) {
            if (idx == real) {
                result = WeightVectors.near(expected[idx], Double.parseDouble(actual[idx]));
            } else {
                result = expected[idx].equals(actual[idx]);
            }
        }
        return result;
    }

    private static String text(final List<String[]> rows) {
        final List<String> lines = new ArrayList<>(rows.size());
        for (final String[] row : rows) {
            lines.add(String.join(" ", row));
        }
        return String.join("; ", lines);
    }
}
