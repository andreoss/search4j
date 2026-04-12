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
import io.github.andreoss.search4j.core.queryparser.QueryParserTokeniser;
import io.github.andreoss.search4j.core.termgen.Stemmer;
import io.github.andreoss.search4j.core.termgen.TermGenerator;
import io.github.andreoss.search4j.core.types.DatabaseAction;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.StemStrategy;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
 * @since 0.1.0
 */
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
final class VidrioDatabaseDifferentialTest {

    @Test
    void acceptsWrittenDatabase(@TempDir final Path temp) throws IOException {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        final Path dbs = temp.resolve("basic");
        this.write(dbs, VidrioDatabaseDifferentialTest::basicDocument);
        final Map<String, String> stats = NativeTools.stats(dbs);
        Assertions.assertEquals("1", stats.get("doccount"));
        Assertions.assertEquals("1", stats.get("highestDocumentIdEverUsed"));
        Assertions.assertEquals(
            "test document",
            NativeTools.data(dbs, 1L).orElse("")
        );
        Assertions.assertTrue(
            NativeTools.integrity(dbs).isEmpty(),
            "reference checker reports errors"
        );
    }

    @Test
    void acceptsManyDocuments(@TempDir final Path temp) throws IOException {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        final Path dbs = temp.resolve("many");
        try (WritableDatabase han = VidrioDatabaseDifferentialTest.open(dbs)) {
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
        final Map<String, String> stats = NativeTools.stats(dbs);
        Assertions.assertEquals("10", stats.get("doccount"));
        Assertions.assertEquals("10", stats.get("highestDocumentIdEverUsed"));
        for (int num = 1; num <= 10; ++num) {
            Assertions.assertEquals(
                String.format("document %d", num),
                NativeTools.data(dbs, num).orElse("")
            );
        }
    }

    @Test
    void recordsPositions(@TempDir final Path temp) throws IOException {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        final Path dbs = temp.resolve("positions");
        this.write(dbs, VidrioDatabaseDifferentialTest::positionedDocument);
        Assertions.assertFalse(
            NativeTools.positions(dbs, "hello", 1L).isEmpty(),
            "reference reader finds no positions"
        );
    }

    @Test
    void keepsValuesAndMetadata(@TempDir final Path temp) throws IOException {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        final Path dbs = temp.resolve("values");
        try (WritableDatabase han = VidrioDatabaseDifferentialTest.open(dbs)) {
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
        Assertions.assertEquals(3, NativeTools.values(dbs, 1L).size());
        Assertions.assertTrue(
            NativeTools.inspect(
                dbs, "postlist", List.of("until")
            ).contains("Key: \\x00\\xc0source"),
            "reference inspector reads no metadata"
        );
    }

    @Test
    void discardsUncommittedDocuments(@TempDir final Path temp)
        throws IOException {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        final Path dbs = temp.resolve("rollback");
        try (WritableDatabase han = VidrioDatabaseDifferentialTest.open(dbs)) {
            final Document doc = DatabaseFactory.document();
            doc.setData("rolled back");
            doc.addTerm("rollback", TermCount.ONE);
            han.addDocument(doc);
        }
        Assertions.assertEquals(
            "0",
            NativeTools.stats(dbs).get("doccount"),
            "uncommitted document persisted"
        );
    }

    @Test
    void passesIntegrityCheck(@TempDir final Path temp) throws IOException {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        final Path dbs = temp.resolve("verify");
        this.write(dbs, VidrioDatabaseDifferentialTest::verifyDocument);
        Assertions.assertTrue(
            NativeTools.integrity(dbs).isEmpty(),
            "reference checker reports errors"
        );
    }

    @Test
    void agreesOnSearchResults(@TempDir final Path temp) throws IOException {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        final Path dbs = temp.resolve("search");
        try (WritableDatabase han = VidrioDatabaseDifferentialTest.open(dbs)) {
            this.addDocument(han, "alpha beta gamma", "alpha", "beta", "gamma");
            this.addDocument(
                han,
                "alpha delta epsilon",
                "alpha",
                "delta",
                "epsilon"
            );
            han.commit();
        }
        final List<Long> found;
        try (Database han = DatabaseFactory.open(dbs)) {
            final Enquire enquire = han.enquire();
            enquire.setQuery(
                new QueryParser(
                    new QueryParserTokeniser("alpha").tokenise(),
                    QueryOp.AND
                ).parse()
            );
            found = enquire.getMSet(0, 10).toList().stream()
                .map(match -> (long) match.docId().value())
                .collect(Collectors.toList());
        }
        Assertions.assertEquals(
            NativeTools.search(dbs, "alpha", 10),
            found,
            "search results differ from the reference"
        );
    }

    @Test
    void agreesOnStatistics(@TempDir final Path temp) throws IOException {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        final Path dbs = temp.resolve("stats");
        try (WritableDatabase han = VidrioDatabaseDifferentialTest.open(dbs)) {
            for (int num = 1; num <= 5; ++num) {
                final Document doc = DatabaseFactory.document();
                doc.setData(String.format("doc %d", num));
                doc.addTerm(String.format("term%d", num), TermCount.ONE);
                doc.addValue(new ValueNo(0), String.format("val%d", num));
                han.addDocument(doc);
            }
            han.commit();
        }
        final Map<String, String> expected = NativeTools.stats(dbs);
        try (Database han = DatabaseFactory.open(dbs)) {
            Assertions.assertEquals(
                expected.get("doccount"),
                String.valueOf(han.docCount().value())
            );
            Assertions.assertEquals(
                expected.get("highestDocumentIdEverUsed"),
                String.valueOf(han.lastDocId().value())
            );
        }
    }

    @Test
    void agreesOnTermFrequencies(@TempDir final Path temp) throws IOException {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        final Path dbs = temp.resolve("frequencies");
        try (WritableDatabase han = VidrioDatabaseDifferentialTest.open(dbs)) {
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
        Assertions.assertEquals(
            Long.valueOf(5),
            NativeTools.termFreqs(dbs).get("apple"),
            "term frequency differs from the reference"
        );
    }

    @Test
    void acceptsEmptyDatabase(@TempDir final Path temp) throws IOException {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        final Path dbs = temp.resolve("empty");
        try (WritableDatabase han = VidrioDatabaseDifferentialTest.open(dbs)) {
            han.commit();
        }
        Assertions.assertEquals("0", NativeTools.stats(dbs).get("doccount"));
        Assertions.assertTrue(NativeTools.integrity(dbs).isEmpty());
    }

    private static WritableDatabase open(final Path dbs) throws IOException {
        return DatabaseFactory.openWritable(
            dbs,
            DatabaseAction.CREATE.code(),
            BlockSize.MIN_BYTES
        );
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

    private void addDocument(
        final WritableDatabase store,
        final String text,
        final String... terms
    ) {
        final Document doc = DatabaseFactory.document();
        doc.setData(text);
        final TermGenerator generator = new TermGenerator();
        generator.stemmer(Stemmer.NONE, StemStrategy.NONE);
        for (final String term : terms) {
            doc.addTerm(term, TermCount.ONE);
        }
        store.addDocument(doc);
    }

    private void write(
        final Path dbs,
        final Consumer<Document> config
    ) throws IOException {
        try (WritableDatabase han = VidrioDatabaseDifferentialTest.open(dbs)) {
            final TermGenerator generator = new TermGenerator();
            generator.stemmer(Stemmer.NONE, StemStrategy.NONE);
            final Document doc = DatabaseFactory.document();
            config.accept(doc);
            doc.addValue(new ValueNo(0), "title");
            han.addDocument(doc);
            han.commit();
        }
    }
}
