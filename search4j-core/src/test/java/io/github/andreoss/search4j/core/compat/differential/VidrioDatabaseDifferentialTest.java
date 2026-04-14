/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.andreoss.search4j.core.compat.differential;

import io.github.andreoss.search4j.core.api.Database;
import io.github.andreoss.search4j.core.api.DatabaseFactory;
import io.github.andreoss.search4j.core.api.Enquire;
import io.github.andreoss.search4j.core.queryparser.QueryParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compares database behaviour against the reference tools over Vidrio
 * databases written by this project: statistics, document data, positions,
 * values, metadata, transactions, integrity checking and search results.
 *
 * <p>Every case writes the same database twice, once with this project and
 * once with the reference writer, and runs the same oracle over both: an
 * oracle the reference database fails is wrong, not the database.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
final class VidrioDatabaseDifferentialTest {

    /**
     * Reason given when the reference tools are not held.
     */
    private static final String ABSENT = "reference not held";

    /**
     * Statistics key of the document count.
     */
    private static final String COUNT = "doccount";

    /**
     * Statistics key of the highest document identifier.
     */
    private static final String LAST = "highestDocumentIdEverUsed";

    /**
     * Message of a rejected integrity check.
     */
    private static final String REJECTED = "reference checker reports errors on %s";

    @Test
    void acceptsWrittenDatabase(@TempDir final Path temp) throws IOException {
        Assumptions.assumeTrue(
            VidrioDatabaseDifferentialTest.held(),
            VidrioDatabaseDifferentialTest.ABSENT
        );
        for (final Path dbs : Scenarios.pair(temp, "basic")) {
            final Map<String, String> stats = NativeTools.stats(dbs);
            Assertions.assertEquals(
                "1", stats.get(VidrioDatabaseDifferentialTest.COUNT)
            );
            Assertions.assertEquals(
                "1", stats.get(VidrioDatabaseDifferentialTest.LAST)
            );
            Assertions.assertEquals(
                "test document",
                NativeTools.data(dbs, 1L).orElse("")
            );
            Assertions.assertEquals(
                List.of(),
                ReferenceDatabases.integrity(dbs),
                String.format(VidrioDatabaseDifferentialTest.REJECTED, dbs)
            );
        }
    }

    @Test
    void acceptsManyDocuments(@TempDir final Path temp) throws IOException {
        Assumptions.assumeTrue(
            VidrioDatabaseDifferentialTest.held(),
            VidrioDatabaseDifferentialTest.ABSENT
        );
        for (final Path dbs : Scenarios.pair(temp, "many")) {
            final Map<String, String> stats = NativeTools.stats(dbs);
            Assertions.assertEquals(
                "10", stats.get(VidrioDatabaseDifferentialTest.COUNT)
            );
            Assertions.assertEquals(
                "10", stats.get(VidrioDatabaseDifferentialTest.LAST)
            );
            for (int num = 1; num <= 10; ++num) {
                Assertions.assertEquals(
                    String.format("document %d", num),
                    NativeTools.data(dbs, num).orElse("")
                );
            }
        }
    }

    @Test
    void recordsPositions(@TempDir final Path temp) throws IOException {
        Assumptions.assumeTrue(
            VidrioDatabaseDifferentialTest.held(),
            VidrioDatabaseDifferentialTest.ABSENT
        );
        for (final Path dbs : Scenarios.pair(temp, "positions")) {
            Assertions.assertFalse(
                NativeTools.positions(dbs, "hello", 1L).isEmpty(),
                String.format("reference reader finds no positions in %s", dbs)
            );
        }
    }

    @Test
    void keepsValuesAndMetadata(@TempDir final Path temp) throws IOException {
        Assumptions.assumeTrue(
            VidrioDatabaseDifferentialTest.held(),
            VidrioDatabaseDifferentialTest.ABSENT
        );
        for (final Path dbs : Scenarios.pair(temp, "values")) {
            Assertions.assertEquals(3, NativeTools.values(dbs, 1L).size());
            Assertions.assertTrue(
                NativeTools.inspect(
                    dbs, "postlist", List.of("until")
                ).contains("Key: \\x00\\xc0source"),
                String.format("reference inspector reads no metadata in %s", dbs)
            );
        }
    }

    @Test
    void commitsOnCloseWithoutTransaction(@TempDir final Path temp)
        throws IOException {
        Assumptions.assumeTrue(
            VidrioDatabaseDifferentialTest.held(),
            VidrioDatabaseDifferentialTest.ABSENT
        );
        for (final Path dbs : Scenarios.pair(temp, "uncommitted")) {
            Assertions.assertEquals(
                "1",
                NativeTools.stats(dbs).get(VidrioDatabaseDifferentialTest.COUNT),
                String.format("close did not commit the document in %s", dbs)
            );
        }
    }

    @Test
    void discardsCancelledTransaction(@TempDir final Path temp)
        throws IOException {
        Assumptions.assumeTrue(
            VidrioDatabaseDifferentialTest.held(),
            VidrioDatabaseDifferentialTest.ABSENT
        );
        for (final Path dbs : Scenarios.pair(temp, "cancelled")) {
            Assertions.assertEquals(
                "0",
                NativeTools.stats(dbs).get(VidrioDatabaseDifferentialTest.COUNT),
                String.format("cancelled document persisted in %s", dbs)
            );
        }
    }

    @Test
    void passesIntegrityCheck(@TempDir final Path temp) throws IOException {
        Assumptions.assumeTrue(
            VidrioDatabaseDifferentialTest.held(),
            VidrioDatabaseDifferentialTest.ABSENT
        );
        for (final Path dbs : Scenarios.pair(temp, "verify")) {
            Assertions.assertEquals(
                List.of(),
                ReferenceDatabases.integrity(dbs),
                String.format(VidrioDatabaseDifferentialTest.REJECTED, dbs)
            );
        }
    }

    @Test
    void agreesOnSearchResults(@TempDir final Path temp) throws IOException {
        Assumptions.assumeTrue(
            VidrioDatabaseDifferentialTest.held(),
            VidrioDatabaseDifferentialTest.ABSENT
        );
        for (final Path dbs : Scenarios.pair(temp, "search")) {
            final List<Long> found;
            try (Database han = DatabaseFactory.open(dbs)) {
                final Enquire enquire = han.enquire();
                enquire.setQuery(new QueryParser().parse("alpha"));
                found = enquire.getMSet(0, 10).toList().stream()
                    .map(match -> (long) match.docId().value())
                    .collect(Collectors.toList());
            }
            Assertions.assertEquals(
                NativeTools.search(dbs, "alpha", 10),
                found,
                String.format("search results differ from the reference on %s", dbs)
            );
        }
    }

    @Test
    void agreesOnStatistics(@TempDir final Path temp) throws IOException {
        Assumptions.assumeTrue(
            VidrioDatabaseDifferentialTest.held(),
            VidrioDatabaseDifferentialTest.ABSENT
        );
        for (final Path dbs : Scenarios.pair(temp, "stats")) {
            final Map<String, String> expected = NativeTools.stats(dbs);
            try (Database han = DatabaseFactory.open(dbs)) {
                Assertions.assertEquals(
                    expected.get(VidrioDatabaseDifferentialTest.COUNT),
                    String.valueOf(han.docCount().value())
                );
                Assertions.assertEquals(
                    expected.get(VidrioDatabaseDifferentialTest.LAST),
                    String.valueOf(han.lastDocId().value())
                );
            }
        }
    }

    @Test
    void agreesOnTermFrequencies(@TempDir final Path temp) throws IOException {
        Assumptions.assumeTrue(
            VidrioDatabaseDifferentialTest.held(),
            VidrioDatabaseDifferentialTest.ABSENT
        );
        for (final Path dbs : Scenarios.pair(temp, "frequencies")) {
            Assertions.assertEquals(
                Long.valueOf(2),
                NativeTools.termFreqs(dbs).get("apple"),
                String.format("term frequency differs from the reference on %s", dbs)
            );
            Assertions.assertEquals(
                Optional.of(5L),
                ReferenceDatabases.collectionFreq(dbs, "apple"),
                String.format("collection frequency differs from the reference on %s", dbs)
            );
        }
    }

    @Test
    void acceptsEmptyDatabase(@TempDir final Path temp) throws IOException {
        Assumptions.assumeTrue(
            VidrioDatabaseDifferentialTest.held(),
            VidrioDatabaseDifferentialTest.ABSENT
        );
        for (final Path dbs : Scenarios.pair(temp, "empty")) {
            Assertions.assertEquals(
                "0",
                NativeTools.stats(dbs).get(VidrioDatabaseDifferentialTest.COUNT)
            );
            Assertions.assertEquals(
                List.of(),
                ReferenceDatabases.integrity(dbs),
                String.format(VidrioDatabaseDifferentialTest.REJECTED, dbs)
            );
        }
    }

    @Test
    void keepsCountsAboveTheSignedRange(@TempDir final Path temp)
        throws IOException {
        Assumptions.assumeTrue(
            NativeTools.available() && ReferenceDatabases.countsWritable(),
            VidrioDatabaseDifferentialTest.ABSENT
        );
        final Path own = temp.resolve("own").resolve("counts");
        Scenarios.writeCounts(own);
        final Path reference = Files.createDirectories(
            temp.resolve("reference")
        ).resolve("counts");
        ReferenceDatabases.writeCounts(reference);
        for (final Path dbs : List.of(reference, own)) {
            final Map<String, String> stats = NativeTools.stats(dbs);
            Assertions.assertEquals(
                List.of("6", "7", "1000000002", "4294967280"),
                List.of(
                    stats.get(VidrioDatabaseDifferentialTest.COUNT),
                    stats.get(VidrioDatabaseDifferentialTest.LAST),
                    stats.get("documentLengthLowerBound"),
                    stats.get("documentLengthUpperBound")
                ),
                String.format("statistics differ from the reference on %s", dbs)
            );
            Assertions.assertEquals(
                List.of(
                    "Posting List for term 'a' (termfreq 3, collfreq 4000000006, wdf_max 3000000000):",
                    "1 3000000000 3000000001", "2 5 2147483660",
                    "4 1000000001 3147483652"
                ),
                NativeTools.postings(dbs, "a"),
                String.format("postings differ from the reference on %s", dbs)
            );
            Assertions.assertEquals(
                List.of(Optional.of(4_294_967_287L), Optional.of(2_147_483_649L)),
                List.of(
                    ReferenceDatabases.collectionFreq(dbs, "c"),
                    ReferenceDatabases.collectionFreq(dbs, "f")
                ),
                String.format("collection frequency differs on %s", dbs)
            );
            Assertions.assertEquals(
                List.of(),
                ReferenceDatabases.integrity(dbs),
                String.format(VidrioDatabaseDifferentialTest.REJECTED, dbs)
            );
        }
    }

    @Test
    void writesBytesOfReference(@TempDir final Path temp) throws IOException {
        Assumptions.assumeTrue(
            VidrioDatabaseDifferentialTest.held(),
            VidrioDatabaseDifferentialTest.ABSENT
        );
        final List<String> oracle = new ArrayList<>(0);
        final List<String> faults = new ArrayList<>(0);
        for (final String name : Scenarios.names()) {
            final List<Path> dbs = Scenarios.pair(temp, name);
            final Path again = Files.createDirectories(temp.resolve("again"))
                .resolve(name);
            ReferenceDatabases.write(name, again);
            oracle.addAll(ReferenceBytes.differences(dbs.get(1), again));
            faults.addAll(ReferenceBytes.differences(dbs.get(1), dbs.get(0)));
        }
        Assertions.assertEquals(
            List.of(), oracle, "reference databases written twice differ"
        );
        Assertions.assertEquals(
            List.of(), faults, "databases differ from the reference bytes"
        );
    }

    @Test
    void writesBytesOfReferenceCounts(@TempDir final Path temp)
        throws IOException {
        Assumptions.assumeTrue(
            NativeTools.available() && ReferenceDatabases.countsWritable(),
            VidrioDatabaseDifferentialTest.ABSENT
        );
        final Path own = temp.resolve("own");
        Scenarios.writeCounts(own);
        final Path reference = temp.resolve("reference");
        ReferenceDatabases.writeCounts(reference);
        final Path again = temp.resolve("again");
        ReferenceDatabases.writeCounts(again);
        Assertions.assertEquals(
            List.of(),
            ReferenceBytes.differences(reference, again),
            "reference databases written twice differ"
        );
        Assertions.assertEquals(
            List.of(),
            ReferenceBytes.differences(reference, own),
            "database differs from the reference bytes"
        );
    }

    private static boolean held() {
        return NativeTools.available() && ReferenceDatabases.writable();
    }
}
