/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.api.DatabaseFactory;
import io.github.andreoss.search4j.core.api.WritableDatabase;
import io.github.andreoss.search4j.core.types.DatabaseAction;
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
 * Pins what iterators kept open across writes return, and the files
 * written, to the output and files of the native library for the same
 * operations, the identifier bytes of the version file excepted.
 *
 * @since 0.9.1
 */
final class OpenIteratorsTest {

    /**
     * Captured field delimiter.
     */
    private static final Pattern SEPARATOR = Pattern.compile("\\t");

    /**
     * Operations of each case, by the case name of the captured rows.
     */
    private static final Map<String, Consumer<IteratorScript>> CASES =
        OpenIteratorsTest.cases();

    @ParameterizedTest(name = "{0}")
    @MethodSource("names")
    void returnsItemsOfReference(final String name, @TempDir final Path temp)
        throws IOException {
        final Path dbs = temp.resolve(name);
        final Map<String, String> found;
        try (
            WritableDatabase han = DatabaseFactory.openWritable(
                dbs, DatabaseAction.CREATE.code(), BlockSize.MIN_BYTES
            )
        ) {
            final IteratorScript run = new IteratorScript(han);
            OpenIteratorsTest.CASES.get(name).accept(run);
            han.commit();
            found = new TreeMap<>();
            found.put("items", OpenIteratorsTest.summary(run.lines()));
        }
        found.putAll(FileDigests.of(dbs));
        Assertions.assertEquals(
            OpenIteratorsTest.expected(name), found,
            String.format("Items or files of %s differ from the reference", name)
        );
    }

    private static Stream<String> names() {
        return OpenIteratorsTest.CASES.keySet().stream();
    }

    private static Map<String, String> expected(final String name)
        throws IOException {
        final Map<String, String> rows = new TreeMap<>();
        try (
            BufferedReader input = new BufferedReader(
                new InputStreamReader(
                    OpenIteratorsTest.class.getResourceAsStream("open-iterators.tsv"),
                    StandardCharsets.UTF_8
                )
            )
        ) {
            String line = input.readLine();
            while (line != null) {
                final String[] row = OpenIteratorsTest.SEPARATOR.split(line, -1);
                if (row[0].equals(name)) {
                    rows.put(row[1], row[2]);
                }
                line = input.readLine();
            }
        }
        return rows;
    }

    private static String summary(final List<String> lines) {
        final StringBuilder text = new StringBuilder();
        for (final String line : lines) {
            text.append(line).append('\n');
        }
        return String.format(
            "%d %s", lines.size(),
            FileDigests.digest(text.toString().getBytes(StandardCharsets.UTF_8))
        );
    }

    private static Map<String, Consumer<IteratorScript>> cases() {
        final Map<String, Consumer<IteratorScript>> map = new TreeMap<>();
        map.put("at", TermScripts::added);
        map.put("atall", TermScripts::everywhere);
        map.put("atgrow", TermScripts::grown);
        map.put("atshrink", TermScripts::shrunk);
        map.put("commit", TermScripts::committed);
        map.put("cancel", TermScripts::cancelled);
        map.put("meta", TermScripts::metadata);
        map.put("pldel", ListScripts::removed);
        map.put("alldocs", ListScripts::documents);
        map.put("allskip", ListScripts::leapedAll);
        map.put("alldel", ListScripts::emptied);
        map.put("vs", ListScripts::merged);
        map.put("vsskip", ListScripts::leaped);
        return map;
    }
}
