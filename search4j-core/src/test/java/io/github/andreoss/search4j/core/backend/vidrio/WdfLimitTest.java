/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Disk and memory writes at the largest unsigned 32-bit frequency and
 * document length, checked against native vectors.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.api.Database;
import io.github.andreoss.search4j.core.api.DatabaseFactory;
import io.github.andreoss.search4j.core.api.Document;
import io.github.andreoss.search4j.core.api.PostingIterator;
import io.github.andreoss.search4j.core.api.TermIterator;
import io.github.andreoss.search4j.core.api.WritableDatabase;
import io.github.andreoss.search4j.core.error.DocNotFoundError;
import io.github.andreoss.search4j.core.types.DatabaseAction;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs the operations of each captured case with frequencies and document
 * lengths at the largest unsigned 32-bit value, and compares the files,
 * statistics, term lists and postings with those the native library gives
 * for the same operations, on disk and in memory. The checker verdicts
 * of the native and the port written files are recorded per case.
 *
 * @since 0.12.0
 */
final class WdfLimitTest {

    /**
     * Captured field delimiter.
     */
    private static final Pattern TAB = Pattern.compile("\\t");

    /**
     * Operation delimiter.
     */
    private static final Pattern SEMI = Pattern.compile(";");

    /**
     * Word delimiter.
     */
    private static final Pattern SPACE = Pattern.compile(" +");

    /**
     * Name of the disk backend in the vectors.
     */
    private static final String DISK = "disk";

    /**
     * Read back of an absent document.
     */
    private static final String MISSING = "missing";

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    void writesFilesAsTheReference(final String name, @TempDir final Path temp)
        throws IOException {
        final Path dbs = temp.resolve(name);
        try (
            WritableDatabase han = DatabaseFactory.openWritable(
                dbs, DatabaseAction.CREATE.code(), BlockSize.MIN_BYTES
            )
        ) {
            WdfLimitTest.run(han, WdfLimitTest.ops(name), new ArrayList<>(8));
        }
        final Map<String, String> expected = new TreeMap<>();
        for (final String[] row : WdfLimitTest.rows(name, WdfLimitTest.DISK)) {
            if ("F".equals(row[2])) {
                expected.put(row[3], row[4]);
            }
        }
        Assertions.assertEquals(
            expected, FileDigests.of(dbs),
            String.format("Files of %s differ from the reference", name)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    void readsBackFromDiskAsTheReference(
        final String name, @TempDir final Path temp
    ) throws IOException {
        final Path dbs = temp.resolve(name);
        final List<String> actual = new ArrayList<>(16);
        try (
            WritableDatabase han = DatabaseFactory.openWritable(
                dbs, DatabaseAction.CREATE.code(), BlockSize.MIN_BYTES
            )
        ) {
            WdfLimitTest.run(han, WdfLimitTest.ops(name), actual);
        }
        try (Database read = DatabaseFactory.open(dbs)) {
            WdfLimitTest.dump(read, actual);
        }
        Assertions.assertEquals(
            WdfLimitTest.reads(name, WdfLimitTest.DISK), actual,
            String.format("Disk read back of %s differs from the reference", name)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    void keepsTheValueInMemoryAsTheReference(final String name)
        throws IOException {
        final List<String> actual = new ArrayList<>(16);
        try (WritableDatabase han = DatabaseFactory.inMemory()) {
            WdfLimitTest.run(han, WdfLimitTest.ops(name), actual);
            WdfLimitTest.dump(han, actual);
        }
        Assertions.assertEquals(
            WdfLimitTest.reads(name, "memory"), actual,
            String.format("Memory read back of %s differs from the reference", name)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    void recordsTheSameCheckerVerdictForBothWriters(final String name)
        throws IOException {
        final Map<String, String> verdicts = new TreeMap<>();
        for (final String[] row : WdfLimitTest.rows(name, WdfLimitTest.DISK)) {
            if ("C".equals(row[2])) {
                verdicts.put(row[3], row[4]);
            }
        }
        Assertions.assertEquals(
            verdicts.get("native"), verdicts.get("port"),
            String.format("Checker verdicts of %s differ by writer", name)
        );
    }

    private static Stream<String> names() throws IOException {
        final List<String> names = new ArrayList<>(32);
        for (final String[] row : WdfLimitTest.all()) {
            if ("ops".equals(row[1])) {
                names.add(row[0]);
            }
        }
        return names.stream();
    }

    private static String ops(final String name) throws IOException {
        String found = "";
        for (final String[] row : WdfLimitTest.all()) {
            if (row[0].equals(name) && "ops".equals(row[1])) {
                found = row[2];
            }
        }
        return found;
    }

    private static List<String> reads(final String name, final String backend)
        throws IOException {
        final List<String> lines = new ArrayList<>(16);
        for (final String[] row : WdfLimitTest.rows(name, backend)) {
            if (!"F".equals(row[2]) && !"C".equals(row[2])) {
                lines.add(String.join("\t", List.of(row).subList(2, row.length)));
            }
        }
        return lines;
    }

    private static List<String[]> rows(final String name, final String backend)
        throws IOException {
        final List<String[]> rows = new ArrayList<>(16);
        for (final String[] row : WdfLimitTest.all()) {
            if (row[0].equals(name) && row[1].equals(backend)) {
                rows.add(row);
            }
        }
        return rows;
    }

    private static List<String[]> all() throws IOException {
        final List<String[]> rows = new ArrayList<>(512);
        try (
            BufferedReader input = new BufferedReader(
                new InputStreamReader(
                    WdfLimitTest.class.getResourceAsStream("wdf-limit.tsv"),
                    StandardCharsets.UTF_8
                )
            )
        ) {
            String line = input.readLine();
            while (line != null) {
                rows.add(WdfLimitTest.TAB.split(line, -1));
                line = input.readLine();
            }
        }
        return rows;
    }

    private static void run(
        final WritableDatabase han, final String ops, final List<String> events
    ) {
        for (final String operation : WdfLimitTest.SEMI.split(ops, -1)) {
            final String[] words = WdfLimitTest.SPACE.split(operation.trim(), -1);
            try {
                WdfLimitTest.step(han, words, events);
            } catch (final DocNotFoundError ex) {
                events.add(String.join("\t", "E", words[0], WdfLimitTest.MISSING));
            }
        }
    }

    private static void step(
        final WritableDatabase han, final String[] words, final List<String> events
    ) {
        final String verb = words[0];
        if ("add".equals(verb)) {
            han.addDocument(WdfLimitTest.document(words, 1));
        } else if ("replace".equals(verb)) {
            han.replaceDocument(
                DocId.parse(words[1]), WdfLimitTest.document(words, 2)
            );
        } else if ("delete".equals(verb)) {
            han.deleteDocument(DocId.parse(words[1]));
        } else if ("commit".equals(verb)) {
            han.commit();
        } else if ("len".equals(verb)) {
            events.add(
                String.join(
                    "\t", "E", "len", words[1],
                    WdfLimitTest.length(han, DocId.parse(words[1]))
                )
            );
        } else if ("post".equals(verb)) {
            events.add(
                String.join("\t", "E", "post", words[1], WdfLimitTest.post(han, words[1]))
            );
        } else if ("pfirst".equals(verb)) {
            events.add(
                String.join(
                    "\t", "E", "pfirst", words[1], WdfLimitTest.listed(han, words[1])
                )
            );
        } else {
            throw new IllegalArgumentException(verb);
        }
    }

    private static Document document(final String[] words, final int from) {
        final Document doc = DatabaseFactory.document();
        if (!"-".equals(words[from])) {
            doc.setData(words[from]);
        }
        for (int idx = from + 1; idx < words.length; ++idx) {
            final String word = words[idx];
            final int plus = word.lastIndexOf('+');
            final TermCount inc = TermCount.parse(word.substring(plus + 1));
            final int mark = word.indexOf('@');
            if (mark >= 0 && mark < plus) {
                doc.addPosting(
                    word.substring(0, mark),
                    TermPos.parse(word.substring(mark + 1, plus)), inc
                );
            } else {
                doc.addTerm(word.substring(0, plus), inc);
            }
        }
        return doc;
    }

    private static void dump(final Database read, final List<String> lines) {
        lines.add(
            String.join(
                "\t", "S", read.lastDocId().toString(), read.docCount().toString(),
                read.totalLength().toString(),
                read.docLengthLowerBound().toString(),
                read.docLengthUpperBound().toString()
            )
        );
        for (int num = 1; num <= read.lastDocId().value(); ++num) {
            final DocId doc = DocId.of(num);
            lines.add(
                String.join(
                    "\t", "D", doc.toString(), WdfLimitTest.length(read, doc),
                    WdfLimitTest.terms(read, doc)
                )
            );
        }
        try (TermIterator terms = read.allTerms("")) {
            while (terms.hasNext()) {
                final String term = terms.next();
                lines.add(String.join("\t", "T", term, WdfLimitTest.post(read, term)));
            }
        }
    }

    private static String length(final Database read, final DocId doc) {
        String result;
        try {
            result = read.docLength(doc).toString();
        } catch (final DocNotFoundError ex) {
            result = WdfLimitTest.MISSING;
        }
        return result;
    }

    private static String terms(final Database read, final DocId doc) {
        final List<String> found = new ArrayList<>(4);
        try (TermIterator terms = read.termList(doc)) {
            while (terms.hasNext()) {
                final String term = terms.next();
                found.add(String.format("%s:%s", term, terms.wdf()));
            }
        } catch (final DocNotFoundError ex) {
            found.clear();
            found.add(WdfLimitTest.MISSING);
        }
        return String.join(" ", found);
    }

    private static String post(final Database read, final String term) {
        return String.join(
            "\t", WdfLimitTest.frequencies(read, term),
            WdfLimitTest.postings(read, term)
        );
    }

    private static String listed(final Database read, final String term) {
        return new StringBuilder(WdfLimitTest.postings(read, term))
            .insert(0, '\t')
            .insert(0, WdfLimitTest.frequencies(read, term))
            .toString();
    }

    private static String frequencies(final Database read, final String term) {
        return String.join(
            "\t", read.termFrequency(term).toString(),
            read.collectionFrequency(term).toString()
        );
    }

    private static String postings(final Database read, final String term) {
        final List<String> found = new ArrayList<>(4);
        try (PostingIterator posts = read.postings(term)) {
            while (posts.hasNext()) {
                final DocId doc = posts.next();
                found.add(String.format("%s:%s", doc, posts.wdf()));
            }
        }
        return String.join(" ", found);
    }
}
