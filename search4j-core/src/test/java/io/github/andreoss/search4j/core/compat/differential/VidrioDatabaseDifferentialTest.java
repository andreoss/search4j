/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.andreoss.search4j.core.compat.differential;

import io.github.andreoss.search4j.core.api.Database;
import io.github.andreoss.search4j.core.api.DatabaseFactory;
import io.github.andreoss.search4j.core.api.Document;
import io.github.andreoss.search4j.core.api.Enquire;
import io.github.andreoss.search4j.core.api.WritableDatabase;
import io.github.andreoss.search4j.core.backend.vidrio.BlockSize;
import io.github.andreoss.search4j.core.queryparser.QueryParser;
import io.github.andreoss.search4j.core.types.DatabaseAction;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
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
        for (final Path dbs : VidrioDatabaseDifferentialTest.pair(
            temp, "basic",
            VidrioDatabaseDifferentialTest.single(
                VidrioDatabaseDifferentialTest::basicDocument
            )
        )) {
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
        for (final Path dbs : VidrioDatabaseDifferentialTest.pair(
            temp, "many", VidrioDatabaseDifferentialTest::manyDocuments
        )) {
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
        for (final Path dbs : VidrioDatabaseDifferentialTest.pair(
            temp, "positions",
            VidrioDatabaseDifferentialTest.single(
                VidrioDatabaseDifferentialTest::positionedDocument
            )
        )) {
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
        for (final Path dbs : VidrioDatabaseDifferentialTest.pair(
            temp, "values", VidrioDatabaseDifferentialTest::valuesDocument
        )) {
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
        for (final Path dbs : VidrioDatabaseDifferentialTest.pair(
            temp, "uncommitted",
            han -> han.addDocument(VidrioDatabaseDifferentialTest.rollback())
        )) {
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
        for (final Path dbs : VidrioDatabaseDifferentialTest.pair(
            temp, "cancelled", VidrioDatabaseDifferentialTest::cancelled
        )) {
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
        for (final Path dbs : VidrioDatabaseDifferentialTest.pair(
            temp, "verify",
            VidrioDatabaseDifferentialTest.single(
                VidrioDatabaseDifferentialTest::verifyDocument
            )
        )) {
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
        for (final Path dbs : VidrioDatabaseDifferentialTest.pair(
            temp, "search", VidrioDatabaseDifferentialTest::searchDocuments
        )) {
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
        for (final Path dbs : VidrioDatabaseDifferentialTest.pair(
            temp, "stats", VidrioDatabaseDifferentialTest::statsDocuments
        )) {
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
        for (final Path dbs : VidrioDatabaseDifferentialTest.pair(
            temp, "frequencies", VidrioDatabaseDifferentialTest::appleDocuments
        )) {
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
        for (final Path dbs : VidrioDatabaseDifferentialTest.pair(
            temp, "empty", WritableDatabase::commit
        )) {
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
        try (WritableDatabase han = VidrioDatabaseDifferentialTest.open(own)) {
            VidrioDatabaseDifferentialTest.largeCounts(han);
        }
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

    private static boolean held() {
        return NativeTools.available() && ReferenceDatabases.writable();
    }

    private static void largeCounts(final WritableDatabase han) {
        han.replaceDocument(
            DocId.of(1),
            VidrioDatabaseDifferentialTest.counted("a:3000000000", "b:1")
        );
        han.replaceDocument(
            DocId.of(2),
            VidrioDatabaseDifferentialTest.counted("a:5", "b:2147483648", "c:7")
        );
        han.replaceDocument(
            DocId.of(3), VidrioDatabaseDifferentialTest.counted("c:4294967280")
        );
        han.replaceDocument(
            DocId.of(4),
            VidrioDatabaseDifferentialTest.counted("a:1000000000", "d:2")
        );
        han.replaceDocument(
            DocId.of(5),
            VidrioDatabaseDifferentialTest.counted("b:3", "e:1", "d:2147483650")
        );
        final Document six = VidrioDatabaseDifferentialTest.counted("e:2");
        six.addPosting("f", new TermPos(1), TermCount.parse("2147483648"));
        six.addPosting("f", new TermPos(2), TermCount.ONE);
        han.replaceDocument(DocId.of(6), six);
        han.replaceDocument(
            DocId.of(4),
            VidrioDatabaseDifferentialTest.counted(
                "a:1000000001", "d:3", "g:2147483648"
            )
        );
        han.replaceDocument(
            DocId.of(7), VidrioDatabaseDifferentialTest.counted("h:4000000000")
        );
        han.deleteDocument(DocId.of(7));
        han.commit();
    }

    private static Document counted(final String... terms) {
        final Document doc = DatabaseFactory.document();
        for (final String term : terms) {
            final int colon = term.indexOf(':');
            doc.addTerm(
                term.substring(0, colon),
                TermCount.parse(term.substring(colon + 1))
            );
        }
        return doc;
    }

    private static List<Path> pair(
        final Path temp,
        final String scenario,
        final Consumer<WritableDatabase> steps
    ) throws IOException {
        final Path own = temp.resolve("own").resolve(scenario);
        try (WritableDatabase han = VidrioDatabaseDifferentialTest.open(own)) {
            steps.accept(han);
        }
        final Path reference = Files.createDirectories(
            temp.resolve("reference")
        ).resolve(scenario);
        ReferenceDatabases.write(scenario, reference);
        return List.of(own, reference);
    }

    private static WritableDatabase open(final Path dbs) throws IOException {
        return DatabaseFactory.openWritable(
            dbs,
            DatabaseAction.CREATE.code(),
            BlockSize.MIN_BYTES
        );
    }

    private static Consumer<WritableDatabase> single(
        final Consumer<Document> config
    ) {
        return han -> {
            final Document doc = DatabaseFactory.document();
            config.accept(doc);
            doc.addValue(new ValueNo(0), "title");
            han.addDocument(doc);
            han.commit();
        };
    }

    private static void basicDocument(final Document doc) {
        doc.setData("test document");
        doc.addTerm("test", TermCount.ONE);
        doc.addTerm("document", TermCount.ONE);
        doc.addValue(new ValueNo(0), "title");
    }

    private static void positionedDocument(final Document doc) {
        doc.setData("hello world hello universe");
        doc.addPosting("hello", new TermPos(1), TermCount.ONE);
        doc.addPosting("world", new TermPos(2), TermCount.ONE);
        doc.addPosting("hello", new TermPos(3), TermCount.ONE);
        doc.addPosting("universe", new TermPos(4), TermCount.ONE);
    }

    private static void verifyDocument(final Document doc) {
        doc.setData("verify test");
        doc.addTerm("verify", TermCount.ONE);
    }

    private static void manyDocuments(final WritableDatabase han) {
        for (int num = 1; num <= 10; ++num) {
            final Document doc = DatabaseFactory.document();
            doc.setData(String.format("document %d", num));
            doc.addTerm("doc", new TermCount(num));
            doc.addTerm(String.format("number%d", num), TermCount.ONE);
            doc.addValue(new ValueNo(0), String.format("title%d", num));
            doc.addValue(
                new ValueNo(1),
                String.format("category%d", num % 3)
            );
            han.addDocument(doc);
        }
        han.commit();
    }

    private static void valuesDocument(final WritableDatabase han) {
        final Document doc = DatabaseFactory.document();
        doc.setData("metadata test");
        doc.addTerm("meta", TermCount.ONE);
        doc.addValue(new ValueNo(0), "author:john");
        doc.addValue(new ValueNo(1), "date:2024");
        doc.addValue(new ValueNo(2), "tags:test,java");
        han.addDocument(doc);
        han.setMetadata("source", "test-suite");
        han.setMetadata("version", "1.0");
        han.commit();
    }

    private static Document rollback() {
        final Document doc = DatabaseFactory.document();
        doc.setData("rolled back");
        doc.addTerm("rollback", TermCount.ONE);
        return doc;
    }

    private static void cancelled(final WritableDatabase han) {
        han.beginTransaction(true);
        han.addDocument(VidrioDatabaseDifferentialTest.rollback());
        han.cancelTransaction();
    }

    private static void searchDocuments(final WritableDatabase han) {
        VidrioDatabaseDifferentialTest.text(
            han, "alpha beta gamma", "alpha", "beta", "gamma"
        );
        VidrioDatabaseDifferentialTest.text(
            han, "alpha delta epsilon", "alpha", "delta", "epsilon"
        );
        han.commit();
    }

    private static void statsDocuments(final WritableDatabase han) {
        for (int num = 1; num <= 5; ++num) {
            final Document doc = DatabaseFactory.document();
            doc.setData(String.format("doc %d", num));
            doc.addTerm(String.format("term%d", num), TermCount.ONE);
            doc.addValue(new ValueNo(0), String.format("val%d", num));
            han.addDocument(doc);
        }
        han.commit();
    }

    private static void appleDocuments(final WritableDatabase han) {
        final Document first = DatabaseFactory.document();
        first.setData("apple apple apple");
        first.addPosting("apple", new TermPos(1), TermCount.ONE);
        first.addPosting("apple", new TermPos(2), TermCount.ONE);
        first.addPosting("apple", new TermPos(3), TermCount.ONE);
        han.addDocument(first);
        final Document second = DatabaseFactory.document();
        second.setData("apple apple");
        second.addPosting("apple", new TermPos(1), TermCount.ONE);
        second.addPosting("apple", new TermPos(2), TermCount.ONE);
        han.addDocument(second);
        han.commit();
    }

    private static void text(
        final WritableDatabase store,
        final String data,
        final String... terms
    ) {
        final Document doc = DatabaseFactory.document();
        doc.setData(data);
        for (final String term : terms) {
            doc.addTerm(term, TermCount.ONE);
        }
        store.addDocument(doc);
    }
}
