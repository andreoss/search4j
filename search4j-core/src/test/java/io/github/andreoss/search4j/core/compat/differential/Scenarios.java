/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.andreoss.search4j.core.compat.differential;

import io.github.andreoss.search4j.core.api.DatabaseFactory;
import io.github.andreoss.search4j.core.api.Document;
import io.github.andreoss.search4j.core.api.WritableDatabase;
import io.github.andreoss.search4j.core.backend.vidrio.BlockSize;
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
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Operations of each database differential case, applied by this project
 * to a database it writes, and the reference writer of the same case.
 *
 * @since 0.8.1
 */
final class Scenarios {

    /**
     * Operations of each case, by case name.
     */
    private static final Map<String, Consumer<WritableDatabase>> STEPS =
        Scenarios.steps();

    /**
     * Utility class, no instances.
     */
    private Scenarios() {
    }

    /**
     * Names of every case the reference writer knows.
     *
     * @return Case names in order
     */
    static List<String> names() {
        return List.copyOf(Scenarios.STEPS.keySet());
    }

    /**
     * Writes the database of one case twice, with this project and with
     * the reference writer.
     *
     * @param temp Directory receiving both databases
     * @param scenario Case name
     * @return Database written by this project, then the reference one
     * @throws IOException When the reference writer fails
     */
    static List<Path> pair(final Path temp, final String scenario)
        throws IOException {
        final Path own = temp.resolve("own").resolve(scenario);
        try (WritableDatabase han = Scenarios.open(own)) {
            Scenarios.STEPS.get(scenario).accept(han);
        }
        final Path reference = Files.createDirectories(
            temp.resolve("reference")
        ).resolve(scenario);
        ReferenceDatabases.write(scenario, reference);
        return List.of(own, reference);
    }

    /**
     * Writes, with this project, the database of the case with counts at
     * and above two to the thirty-one.
     *
     * @param dbs Database directory to create
     * @throws IOException When the database cannot be created
     */
    static void writeCounts(final Path dbs) throws IOException {
        try (WritableDatabase han = Scenarios.open(dbs)) {
            Scenarios.largeCounts(han);
        }
    }

    private static Map<String, Consumer<WritableDatabase>> steps() {
        final Map<String, Consumer<WritableDatabase>> map = new TreeMap<>();
        map.put("empty", WritableDatabase::commit);
        map.put("basic", Scenarios.single(Scenarios::basicDocument));
        map.put("positions", Scenarios.single(Scenarios::positionedDocument));
        map.put("verify", Scenarios.single(Scenarios::verifyDocument));
        map.put("many", Scenarios::manyDocuments);
        map.put("values", Scenarios::valuesDocument);
        map.put("uncommitted", han -> han.addDocument(Scenarios.rollback()));
        map.put("cancelled", Scenarios::cancelled);
        map.put("search", Scenarios::searchDocuments);
        map.put("stats", Scenarios::statsDocuments);
        map.put("frequencies", Scenarios::appleDocuments);
        return map;
    }

    private static void largeCounts(final WritableDatabase han) {
        han.replaceDocument(
            DocId.of(1),
            Scenarios.counted("a:3000000000", "b:1")
        );
        han.replaceDocument(
            DocId.of(2),
            Scenarios.counted("a:5", "b:2147483648", "c:7")
        );
        han.replaceDocument(
            DocId.of(3), Scenarios.counted("c:4294967280")
        );
        han.replaceDocument(
            DocId.of(4),
            Scenarios.counted("a:1000000000", "d:2")
        );
        han.replaceDocument(
            DocId.of(5),
            Scenarios.counted("b:3", "e:1", "d:2147483650")
        );
        final Document six = Scenarios.counted("e:2");
        six.addPosting("f", new TermPos(1), TermCount.parse("2147483648"));
        six.addPosting("f", new TermPos(2), TermCount.ONE);
        han.replaceDocument(DocId.of(6), six);
        han.replaceDocument(
            DocId.of(4),
            Scenarios.counted(
                "a:1000000001", "d:3", "g:2147483648"
            )
        );
        han.replaceDocument(
            DocId.of(7), Scenarios.counted("h:4000000000")
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
        han.addDocument(Scenarios.rollback());
        han.cancelTransaction();
    }

    private static void searchDocuments(final WritableDatabase han) {
        Scenarios.text(
            han, "alpha beta gamma", "alpha", "beta", "gamma"
        );
        Scenarios.text(
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
