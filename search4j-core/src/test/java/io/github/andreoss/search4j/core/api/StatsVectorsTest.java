/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.backend.vidrio.VidrioFormat;
import io.github.andreoss.search4j.core.error.SearchError;
import io.github.andreoss.search4j.core.types.DatabaseAction;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Replays statistics captured from the reference library after the stored
 * statistics of a small database were replaced: counts and last document
 * numbers that wrap, totals of two to the sixty-third and above, decoding
 * errors, weights of every scheme, and the statistics after one more
 * document.
 *
 * @since 0.12.0
 */
final class StatsVectorsTest {

    /**
     * Separator of the items of a match set.
     */
    private static final Pattern SPACE = Pattern.compile(" ");

    /**
     * Queries searched, by name: a union and synonyms over intersections,
     * differences, exclusive unions and every document.
     */
    private static final Map<String, Query> QUERIES = Map.of(
        "or", StatsVectorsTest.pair(
            QueryOp.OR, Query.term("a"),
            StatsVectorsTest.pair(QueryOp.OR, Query.term("b"), Query.term("c"))
        ),
        "syn", StatsVectorsTest.pair(QueryOp.SYNONYM, Query.term("a"), Query.term("b")),
        "synand", StatsVectorsTest.pair(
            QueryOp.SYNONYM, StatsVectorsTest.pair(QueryOp.AND, Query.term("a"), Query.term("c")),
            Query.term("b")
        ),
        "synandnot", StatsVectorsTest.pair(
            QueryOp.SYNONYM,
            StatsVectorsTest.pair(QueryOp.AND_NOT, Query.term("a"), Query.term("b")),
            Query.term("c")
        ),
        "synxor", StatsVectorsTest.pair(
            QueryOp.SYNONYM, StatsVectorsTest.pair(QueryOp.XOR, Query.term("a"), Query.term("b")),
            Query.term("c")
        ),
        "synall", StatsVectorsTest.pair(QueryOp.SYNONYM, Query.MATCH_ALL, Query.term("a")),
        "all", Query.MATCH_ALL,
        "a", Query.term("a")
    );

    @Test
    void matchesTheReferenceAtWideStatistics(@TempDir final Path temp)
        throws IOException {
        final Map<String, List<String[]>> cases = new LinkedHashMap<>(32);
        for (final String[] row : WeightVectors.rows("stats-vectors.tsv")) {
            cases.computeIfAbsent(row[0], key -> new ArrayList<>(96)).add(row);
        }
        final Path base = temp.resolve("_seed");
        StatsVectorsTest.seed(base);
        final List<String> failures = new ArrayList<>(0);
        int checked = 0;
        for (final Map.Entry<String, List<String[]>> entry : cases.entrySet()) {
            final Map<String, String> actual =
                StatsVectorsTest.replay(base, temp.resolve(entry.getKey()), entry.getValue());
            for (final String[] row : entry.getValue()) {
                checked = checked + 1;
                if (!StatsVectorsTest.same(row[1], row[2], actual.get(row[1]))) {
                    failures.add(
                        String.format(
                            "%s %s expected [%s] got [%s]", row[0], row[1], row[2],
                            actual.get(row[1])
                        )
                    );
                }
            }
            if (actual.size() != entry.getValue().size()) {
                failures.add(
                    String.format("%s produced %s", entry.getKey(), actual.keySet())
                );
            }
        }
        Assertions.assertEquals(
            List.of(List.of(), 35_610, 18), List.of(failures, checked, cases.size()),
            "Statistics, weights and additions at every captured case"
        );
    }

    private static void seed(final Path base) {
        try (
            WritableDatabase han = DatabaseFactory.openWritable(
                base, DatabaseAction.CREATE.code(), 2048
            )
        ) {
            han.addDocument(StatsVectorsTest.doc("a", 2L, "b", 1L));
            han.addDocument(StatsVectorsTest.doc("b", 1L, "c", 1L));
            han.addDocument(StatsVectorsTest.doc("a", 1L, "c", 3L));
            han.commit();
        }
    }

    private static Map<String, String> replay(
        final Path base, final Path dir, final List<String[]> rows
    ) throws IOException {
        final Map<String, String> out = new LinkedHashMap<>(96);
        Files.createDirectories(dir);
        try (Stream<Path> files = Files.list(base)) {
            for (final Path file : files.toList()) {
                Files.copy(file, dir.resolve(file.getFileName()));
            }
        }
        final Path version = dir.resolve(VidrioFormat.versionFileName());
        final byte[] old = Files.readAllBytes(version);
        final byte[] tail = HexFormat.of().parseHex("0300020301000900");
        Assertions.assertArrayEquals(
            tail, Arrays.copyOfRange(old, old.length - tail.length, old.length),
            "Statistics of the base database"
        );
        final byte[] stats = HexFormat.of().parseHex(rows.get(0)[2]);
        final byte[] fresh = Arrays.copyOf(old, old.length - tail.length + stats.length);
        System.arraycopy(stats, 0, fresh, old.length - tail.length, stats.length);
        Files.write(version, fresh);
        out.put("bytes", HexFormat.of().formatHex(stats));
        boolean opened = true;
        try (Database read = DatabaseFactory.open(dir)) {
            StatsVectorsTest.stats(out, "open", read);
            for (final String[] row : rows) {
                if (row[1].startsWith("mset ")) {
                    out.put(row[1], StatsVectorsTest.mset(read, row[1].substring(5)));
                } else if (row[1].startsWith("bounds ")) {
                    out.put(row[1], StatsVectorsTest.bounds(read, row[1].substring(7)));
                }
            }
        } catch (final SearchError ex) {
            out.put("open error", String.join(" ", ex.getType(), ex.getMessage()));
            opened = false;
        }
        if (opened) {
            StatsVectorsTest.add(out, dir);
        }
        return out;
    }

    private static void add(final Map<String, String> out, final Path dir) {
        boolean added = true;
        try (
            WritableDatabase han = DatabaseFactory.openWritable(
                dir, DatabaseAction.OPEN.code()
            )
        ) {
            out.put(
                "added",
                Integer.toString(
                    han.addDocument(StatsVectorsTest.doc("a", 1L, "d", 2L)).value()
                )
            );
            StatsVectorsTest.stats(out, "pending", han);
            han.commit();
        } catch (final SearchError ex) {
            out.put("add error", String.join(" ", ex.getType(), ex.getMessage()));
            added = false;
        }
        if (added) {
            try (Database read = DatabaseFactory.open(dir)) {
                StatsVectorsTest.stats(out, "committed", read);
            }
        }
    }

    private static void stats(
        final Map<String, String> out, final String tag, final Database base
    ) {
        out.put(String.join(" ", tag, "docs"), base.docCount().toString());
        out.put(String.join(" ", tag, "last"), Integer.toString(base.lastDocId().value()));
        out.put(String.join(" ", tag, "total"), base.totalLength().toString());
        out.put(String.join(" ", tag, "average"), Double.toString(base.averageLength().value()));
        out.put(String.join(" ", tag, "lower"), base.docLengthLowerBound().toString());
        out.put(String.join(" ", tag, "upper"), base.docLengthUpperBound().toString());
        out.put(String.join(" ", tag, "wdf"), base.wdfUpperBound("a").toString());
    }

    private static String mset(final Database base, final String key) {
        final int space = key.indexOf(' ');
        String result;
        try {
            final Enquire enquire = base.enquire();
            enquire.setQuery(StatsVectorsTest.QUERIES.get(key.substring(0, space)));
            enquire.setWeightingScheme(WeightVectors.parse(key.substring(space + 1)));
            final StringBuilder items = new StringBuilder(64);
            for (final MSet.Item item : enquire.getMSet(0, 10).toList()) {
                items.append(item.docId().value()).append(':')
                    .append(item.weight()).append(' ');
            }
            result = items.toString();
        } catch (final SearchError ex) {
            result = String.join(" ", "ERROR", ex.getType(), ex.getMessage());
        }
        return result;
    }

    private static String bounds(final Database base, final String key) {
        final String[] parts = StatsVectorsTest.SPACE.split(key, 3);
        final String[] window = parts[1].split(",", -1);
        String result;
        try {
            final Enquire enquire = base.enquire();
            enquire.setQuery(StatsVectorsTest.QUERIES.get(parts[0]));
            enquire.setWeightingScheme(WeightVectors.parse(parts[2]));
            final MSet found = enquire.getMSet(
                Integer.parseInt(window[0]), Integer.parseInt(window[1]),
                Integer.parseInt(window[2])
            );
            result = String.join(
                " ", found.size().toString(),
                found.matchesLowerBound().toString(),
                found.matchesEstimated().toString(),
                found.matchesUpperBound().toString()
            );
        } catch (final SearchError ex) {
            result = String.join(" ", "ERROR", ex.getType(), ex.getMessage());
        }
        return result;
    }

    private static boolean same(final String key, final String expected, final String actual) {
        boolean result = expected.equals(actual);
        if (!result && actual != null && key.endsWith(" average")) {
            result = Double.parseDouble(expected) == Double.parseDouble(actual);
        } else if (!result && actual != null && key.startsWith("mset ")
            && !expected.startsWith("ERROR")) {
            result = StatsVectorsTest.close(expected, actual);
        }
        return result;
    }

    private static boolean close(final String expected, final String actual) {
        final String[] want = StatsVectorsTest.SPACE.split(expected.trim(), -1);
        final String[] got = StatsVectorsTest.SPACE.split(actual.trim(), -1);
        boolean result = want.length == got.length;
        for (int idx = 0; result && idx < want.length; ++idx) {
            final String[] left = want[idx].split(":", -1);
            final String[] right = got[idx].split(":", -1);
            final double one = Double.parseDouble(left[1]);
            final double two = Double.parseDouble(right[1]);
            result = left[0].equals(right[0])
                && (one == two
                || Math.abs(one - two) <= 1e-9 * Math.max(Math.abs(one), Math.abs(two)));
        }
        return result;
    }

    private static Document doc(
        final String first, final long one, final String second, final long two
    ) {
        final Document doc = DatabaseFactory.document();
        doc.addTerm(first, new TermCount(one));
        doc.addTerm(second, new TermCount(two));
        return doc;
    }

    private static Query pair(final QueryOp type, final Query left, final Query right) {
        return Query.of(type, List.of(left, right));
    }
}
