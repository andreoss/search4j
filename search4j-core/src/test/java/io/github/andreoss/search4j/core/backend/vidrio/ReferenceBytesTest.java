/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.api.DatabaseFactory;
import io.github.andreoss.search4j.core.api.Document;
import io.github.andreoss.search4j.core.api.PositionIterator;
import io.github.andreoss.search4j.core.api.PostingIterator;
import io.github.andreoss.search4j.core.api.ValueIterator;
import io.github.andreoss.search4j.core.api.WritableDatabase;
import io.github.andreoss.search4j.core.types.DatabaseAction;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pins the bytes of every file of databases written by this project to the
 * digests of the files the native library writes for the same operations,
 * the identifier bytes of the version file excepted.
 *
 * @since 0.8.1
 */
final class ReferenceBytesTest {

    /**
     * Captured digest field delimiter.
     */
    private static final Pattern SEPARATOR = Pattern.compile("\\t");

    /**
     * Operations of each case, by the case name of the captured digests.
     */
    private static final Map<String, Consumer<WritableDatabase>> CASES =
        ReferenceBytesTest.cases();

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    void writesBytesOfReference(final String name, @TempDir final Path temp)
        throws IOException {
        final Path dbs = temp.resolve(name);
        try (
            WritableDatabase han = DatabaseFactory.openWritable(
                dbs, DatabaseAction.CREATE.code(), BlockSize.MIN_BYTES
            )
        ) {
            ReferenceBytesTest.CASES.get(name).accept(han);
        }
        Assertions.assertEquals(
            ReferenceBytesTest.expected(name),
            FileDigests.of(dbs),
            String.format("Files of %s differ from the reference", name)
        );
    }

    private static Stream<String> names() {
        return ReferenceBytesTest.CASES.keySet().stream();
    }

    private static Map<String, String> expected(final String name)
        throws IOException {
        final Map<String, String> rows = new TreeMap<>();
        try (
            BufferedReader input = new BufferedReader(
                new InputStreamReader(
                    ReferenceBytesTest.class.getResourceAsStream(
                        "reference-bytes.tsv"
                    ),
                    StandardCharsets.UTF_8
                )
            )
        ) {
            String line = input.readLine();
            while (line != null) {
                final String[] row = ReferenceBytesTest.SEPARATOR.split(line, -1);
                if (row[0].equals(name)) {
                    rows.put(row[1], row[2]);
                }
                line = input.readLine();
            }
        }
        return rows;
    }

    private static Map<String, Consumer<WritableDatabase>> cases() {
        final Map<String, Consumer<WritableDatabase>> map = new TreeMap<>();
        map.put("empty", WritableDatabase::commit);
        map.put(
            "single",
            ReferenceBytesTest.steps(
                han -> han.addDocument(ReferenceBytesTest.plain("one", "alpha", "beta"))
            )
        );
        map.put(
            "values",
            ReferenceBytesTest.steps(
                han -> {
                    for (int num = 1; num <= 5; ++num) {
                        han.addDocument(ReferenceBytesTest.valued("v", num));
                    }
                }
            )
        );
        map.put(
            "onevalue",
            ReferenceBytesTest.steps(
                han -> {
                    final Document doc = ReferenceBytesTest.plain("one", "alpha");
                    doc.addValue(new ValueNo(0), "x");
                    han.addDocument(doc);
                }
            )
        );
        map.put(
            "positions",
            ReferenceBytesTest.steps(
                han -> han.addDocument(
                    ReferenceBytesTest.placed("p", "hello", "world", "hello")
                )
            )
        );
        map.put(
            "mixed",
            ReferenceBytesTest.steps(
                han -> {
                    han.addDocument(ReferenceBytesTest.placed("p", "hello", "world"));
                    han.addDocument(ReferenceBytesTest.plain("q", "hello", "there"));
                    han.addDocument(ReferenceBytesTest.placed("r", "world", "again"));
                }
            )
        );
        ReferenceBytesTest.replacements(map);
        map.put(
            "delete",
            ReferenceBytesTest.steps(
                han -> {
                    for (int num = 1; num <= 3; ++num) {
                        han.addDocument(ReferenceBytesTest.valued("d", num));
                    }
                },
                han -> han.deleteDocument(DocId.of(2))
            )
        );
        map.put(
            "commits",
            ReferenceBytesTest.steps(
                han -> han.addDocument(ReferenceBytesTest.plain("a", "alpha")),
                han -> han.addDocument(ReferenceBytesTest.placed("b", "alpha", "beta")),
                han -> han.addDocument(ReferenceBytesTest.valued("c", 3)),
                han -> han.deleteDocument(DocId.of(1))
            )
        );
        map.put("bulk", ReferenceBytesTest::bulk);
        map.put("highslots", ReferenceBytesTest::highSlots);
        map.put("midvalues", han -> ReferenceBytesTest.midSession(han, false));
        map.put("midcommits", han -> ReferenceBytesTest.midSession(han, true));
        map.put("midpositions", ReferenceBytesTest::midPositions);
        map.put("deepadds", ReferenceBytesTest::deepAdds);
        map.put("deepfree", ReferenceBytesTest::deepFree);
        map.put("deepreads", ReferenceBytesTest::deepReads);
        return map;
    }

    private static void replacements(
        final Map<String, Consumer<WritableDatabase>> map
    ) {
        map.put(
            "replacepos",
            ReferenceBytesTest.replaced(
                ReferenceBytesTest.placed("p", "hello", "world"),
                ReferenceBytesTest.placed("r", "world", "again")
            )
        );
        map.put(
            "replacenopos",
            ReferenceBytesTest.replaced(
                ReferenceBytesTest.plain("p", "hello", "world"),
                ReferenceBytesTest.plain("r", "world", "again")
            )
        );
        map.put(
            "replacedrop",
            ReferenceBytesTest.replaced(
                ReferenceBytesTest.placed("p", "hello", "world"),
                ReferenceBytesTest.plain("r", "world", "again")
            )
        );
        map.put(
            "replacenew",
            ReferenceBytesTest.steps(
                han -> han.replaceDocument(
                    DocId.of(3), ReferenceBytesTest.plain("r", "world", "again")
                )
            )
        );
    }

    private static void midSession(final WritableDatabase han, final boolean twice) {
        han.replaceDocument(DocId.of(17), ReferenceBytesTest.valued("m", 3));
        han.replaceDocument(DocId.of(2), ReferenceBytesTest.valued("m", 4));
        han.replaceDocument(DocId.of(33), ReferenceBytesTest.valued("m", 1));
        ReferenceBytesTest.stream(han, 0);
        han.addDocument(ReferenceBytesTest.valued("m", 2));
        han.replaceDocument(DocId.of(5), ReferenceBytesTest.valued("m", 5));
        ReferenceBytesTest.stream(han, 1);
        if (twice) {
            han.commit();
            han.replaceDocument(DocId.of(2), ReferenceBytesTest.valued("n", 1));
            han.deleteDocument(DocId.of(17));
            ReferenceBytesTest.stream(han, 3);
            han.addDocument(ReferenceBytesTest.valued("n", 5));
            ReferenceBytesTest.stream(han, 7);
        }
        han.commit();
    }

    private static void midPositions(final WritableDatabase han) {
        han.addDocument(ReferenceBytesTest.placed("a", "hello", "world", "hello"));
        han.addDocument(ReferenceBytesTest.placed("b", "world", "hello"));
        han.addDocument(ReferenceBytesTest.placed("c", "hello", "alpha"));
        han.commit();
        han.addDocument(
            ReferenceBytesTest.placed("d", "alpha", "hello", "world", "hello")
        );
        han.replaceDocument(
            DocId.of(2), ReferenceBytesTest.placed("e", "hello", "hello", "world")
        );
        han.replaceDocument(
            DocId.of(3), ReferenceBytesTest.plain("f", "hello", "alpha")
        );
        ReferenceBytesTest.positions(han);
        han.addDocument(ReferenceBytesTest.placed("g", "world", "hello"));
        han.deleteDocument(DocId.of(1));
        ReferenceBytesTest.positions(han);
        han.commit();
    }

    private static void positions(final WritableDatabase han) {
        for (final String term : List.of("hello", "world", "alpha")) {
            try (PostingIterator posts = han.postings(term)) {
                while (posts.hasNext()) {
                    final DocId doc = posts.next();
                    ReferenceBytesTest.drain(posts.positions());
                    ReferenceBytesTest.drain(han.positions(doc, term));
                }
            }
        }
    }

    private static void deepAdds(final WritableDatabase han) {
        ReferenceBytesTest.many(han, 1, 2500);
        han.commit();
        ReferenceBytesTest.many(han, 2501, 2600);
        for (int num = 1100; num <= 1300; num += 3) {
            han.deleteDocument(DocId.of(num));
        }
        ReferenceBytesTest.many(han, 2601, 2700);
        han.commit();
    }

    private static void deepFree(final WritableDatabase han) {
        ReferenceBytesTest.many(han, 1, 1500);
        han.commit();
        ReferenceBytesTest.many(han, 1501, 1700);
        for (int num = 1501; num <= 1700; ++num) {
            han.deleteDocument(DocId.of(num));
        }
        han.commit();
    }

    private static void deepReads(final WritableDatabase han) {
        ReferenceBytesTest.many(han, 1, 1500);
        ReferenceBytesTest.common(han);
        ReferenceBytesTest.many(han, 1501, 2500);
        ReferenceBytesTest.common(han);
        han.commit();
        ReferenceBytesTest.common(han);
        ReferenceBytesTest.many(han, 2501, 2600);
        for (int num = 1100; num <= 1300; num += 3) {
            han.deleteDocument(DocId.of(num));
        }
        ReferenceBytesTest.common(han);
        ReferenceBytesTest.many(han, 2601, 2700);
        ReferenceBytesTest.common(han);
        han.commit();
        ReferenceBytesTest.common(han);
    }

    private static void common(final WritableDatabase han) {
        for (final String term : List.of("common", "u77")) {
            try (PostingIterator posts = han.postings(term)) {
                while (posts.hasNext()) {
                    posts.next();
                }
            }
        }
        if (han.lastDocId().value() >= 1200) {
            ReferenceBytesTest.drain(han.positions(DocId.of(1200), "common"));
        }
        ReferenceBytesTest.stream(han, 0);
    }

    private static void many(final WritableDatabase han, final int from, final int upto) {
        for (int num = from; num <= upto; ++num) {
            final String[] terms = {
                "common", String.format("u%d", num % 997),
                String.format("k%d", num), "common",
            };
            final Document doc;
            if (num % 2 == 0) {
                doc = ReferenceBytesTest.placed(String.format("doc%d", num), terms);
            } else {
                doc = ReferenceBytesTest.plain(String.format("doc%d", num), terms);
                doc.addTerm("common", TermCount.ONE);
            }
            doc.addValue(new ValueNo(0), String.format("val%07d", num * 31 % 100_003));
            if (num % 5 == 0) {
                doc.addValue(new ValueNo(4), String.format("five%d", num));
            }
            han.addDocument(doc);
        }
    }

    private static void drain(final PositionIterator positions) {
        try (positions) {
            while (positions.hasNext()) {
                positions.next();
            }
        }
    }

    private static void stream(final WritableDatabase han, final int slot) {
        try (ValueIterator values = han.valueStream(new ValueNo(slot))) {
            while (values.hasNext()) {
                values.next();
            }
        }
    }

    private static void highSlots(final WritableDatabase han) {
        final int[] slots = {1000, 2, 65, 17, 3, 100, 33};
        for (int num = 1; num <= 6; ++num) {
            final Document doc = ReferenceBytesTest.plain("h", "h");
            for (int idx = 0; idx < slots.length; ++idx) {
                if ((num + idx) % 3 != 0) {
                    doc.addValue(
                        new ValueNo(slots[idx]), String.format("v%d-%d", num, idx)
                    );
                }
            }
            han.addDocument(doc);
        }
        han.commit();
        han.replaceDocument(DocId.of(2), ReferenceBytesTest.plain("h", "g"));
        han.deleteDocument(DocId.of(4));
        han.commit();
    }

    private static void bulk(final WritableDatabase han) {
        for (int num = 1; num <= 400; ++num) {
            final String first = String.format("t%d", num % 37);
            final String second = String.format("w%d", num);
            final Document doc;
            if (num % 3 == 0) {
                doc = ReferenceBytesTest.placed("bulk", first, second, first);
            } else {
                doc = ReferenceBytesTest.plain("bulk", first, second);
            }
            doc.addValue(new ValueNo(0), String.format("s%05d", num * 13 % 1000));
            if (num % 4 != 0) {
                doc.addValue(new ValueNo(2), String.format("r%d", num % 11));
            }
            han.addDocument(doc);
            if (num % 100 == 0) {
                han.commit();
            }
        }
        for (int num = 10; num <= 400; num += 10) {
            final String term = String.format("t%d", num % 5);
            final Document doc;
            if (num % 20 == 0) {
                doc = ReferenceBytesTest.placed("again", term, "new");
            } else {
                doc = ReferenceBytesTest.plain("again", term, "new");
            }
            doc.addValue(new ValueNo(1), "q");
            han.replaceDocument(DocId.of(num), doc);
        }
        for (int num = 7; num <= 400; num += 7) {
            han.deleteDocument(DocId.of(num));
        }
        han.commit();
    }

    private static Consumer<WritableDatabase> replaced(
        final Document first, final Document second
    ) {
        return ReferenceBytesTest.steps(
            han -> han.addDocument(first),
            han -> han.replaceDocument(DocId.of(1), second)
        );
    }

    @SafeVarargs
    private static Consumer<WritableDatabase> steps(
        final Consumer<WritableDatabase>... each
    ) {
        return han -> {
            for (final Consumer<WritableDatabase> step : List.of(each)) {
                step.accept(han);
                han.commit();
            }
        };
    }

    private static Document plain(final String data, final String... terms) {
        final Document doc = DatabaseFactory.document();
        doc.setData(data);
        for (final String term : terms) {
            doc.addTerm(term, TermCount.ONE);
        }
        return doc;
    }

    private static Document placed(final String data, final String... terms) {
        final Document doc = DatabaseFactory.document();
        doc.setData(data);
        for (int idx = 0; idx < terms.length; ++idx) {
            doc.addPosting(terms[idx], new TermPos(idx + 1), TermCount.ONE);
        }
        return doc;
    }

    private static Document valued(final String data, final int num) {
        final String parity;
        if (num % 2 == 1) {
            parity = "odd";
        } else {
            parity = "even";
        }
        final Document doc = ReferenceBytesTest.plain(data, "v", parity);
        doc.addValue(new ValueNo(0), String.format("a%d", num));
        doc.addValue(new ValueNo(1), String.format("b%d", 10 - num));
        if (num % 2 == 1) {
            doc.addValue(new ValueNo(3), String.format("c%d", num));
        }
        doc.addValue(new ValueNo(7), String.format("z%d", num * 7));
        return doc;
    }
}
