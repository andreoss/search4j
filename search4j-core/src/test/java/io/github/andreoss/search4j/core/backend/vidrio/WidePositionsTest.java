/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.api.Database;
import io.github.andreoss.search4j.core.api.DatabaseFactory;
import io.github.andreoss.search4j.core.api.Document;
import io.github.andreoss.search4j.core.api.PositionIterator;
import io.github.andreoss.search4j.core.api.TermIterator;
import io.github.andreoss.search4j.core.api.WritableDatabase;
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
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reads back position lists at and above two to the thirty-one from a disk
 * database and compares them with what the reference library reads from
 * the same database written natively.
 *
 * @since 0.12.0
 */
final class WidePositionsTest {

    /**
     * Separator of a term and its positions.
     */
    private static final Pattern SPACE = Pattern.compile(" ");

    @Test
    void readsWidePositionsAsTheReference(@TempDir final Path temp)
        throws IOException {
        final Path dbs = temp.resolve("widepositions");
        try (
            WritableDatabase han = DatabaseFactory.openWritable(
                dbs, DatabaseAction.CREATE.code(), BlockSize.MIN_BYTES
            )
        ) {
            WidePositionsTest.write(han);
        }
        Assertions.assertEquals(
            WidePositionsTest.expected(), WidePositionsTest.actual(dbs),
            "Positions at and above two to the thirty-one read back as the reference"
        );
    }

    /**
     * Adds the two documents with wide positions and commits.
     *
     * @param han Database written
     */
    static void write(final WritableDatabase han) {
        han.addDocument(
            WidePositionsTest.at(
                "w1", "alpha 4294967295", "beta 1 4294967294",
                "hello 2147483647 2147483648 4294967295",
                "world 5 2147483649 3000000000 4294967293"
            )
        );
        han.addDocument(
            WidePositionsTest.at(
                "w2", "hello 3000000000", "world 2147483648 2147483650"
            )
        );
        han.commit();
    }

    private static List<String> expected() throws IOException {
        final List<String> expected = new ArrayList<>(8);
        try (
            BufferedReader input = new BufferedReader(
                new InputStreamReader(
                    WidePositionsTest.class.getResourceAsStream("wide-positions.tsv"),
                    StandardCharsets.UTF_8
                )
            )
        ) {
            String line = input.readLine();
            while (line != null) {
                expected.add(line);
                line = input.readLine();
            }
        }
        return expected;
    }

    private static List<String> actual(final Path dbs) {
        final List<String> actual = new ArrayList<>(8);
        try (Database read = DatabaseFactory.open(dbs)) {
            for (int doc = 1; doc <= read.lastDocId().value(); ++doc) {
                final TermIterator terms = read.termList(DocId.of(doc));
                while (terms.hasNext()) {
                    final String term = terms.next();
                    final StringBuilder row = new StringBuilder(64).append(
                        String.join(
                            "\t", "P", Integer.toString(doc), term,
                            terms.positionCount().toString()
                        )
                    );
                    final PositionIterator positions =
                        read.positions(DocId.of(doc), term);
                    while (positions.hasNext()) {
                        row.append(' ').append(positions.next());
                    }
                    actual.add(row.toString());
                }
            }
            actual.add(
                String.join(
                    "\t", "L", read.docCount().toString(),
                    read.docLengthUpperBound().toString(),
                    read.totalLength().toString()
                )
            );
        }
        return actual;
    }

    private static Document at(final String data, final String... terms) {
        final Document doc = DatabaseFactory.document();
        doc.setData(data);
        for (final String term : terms) {
            final String[] parts = WidePositionsTest.SPACE.split(term, -1);
            for (int idx = 1; idx < parts.length; ++idx) {
                doc.addPosting(parts[0], TermPos.parse(parts[idx]), TermCount.ONE);
            }
        }
        return doc;
    }
}
