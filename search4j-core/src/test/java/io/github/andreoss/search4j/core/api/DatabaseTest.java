/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API contracts.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TotalLength;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of the {@link Database} and {@link WritableDatabase} contracts.
 *
 * @since 0.1.0
 */
final class DatabaseTest {

    @Test
    void addDocumentIncrementsCount() {
        final StubDatabase database = new StubDatabase();
        database.addDocument(new StubDocument());
        Assertions.assertEquals(
            new TermCount(1), database.docCount(), "Document count"
        );
    }

    @Test
    void addDocumentReturnsDocId() {
        Assertions.assertEquals(
            DocId.of(1),
            new StubDatabase().addDocument(new StubDocument()),
            "New doc id"
        );
    }

    @Test
    void deleteDocumentDecrementsCount() {
        final StubDatabase database = new StubDatabase();
        database.deleteDocument(database.addDocument(new StubDocument()));
        Assertions.assertEquals(
            TermCount.ZERO, database.docCount(), "Count after delete"
        );
    }

    @Test
    void emptyDatabaseHasZeroTotal() {
        Assertions.assertEquals(
            new TotalLength(0L), new StubDatabase().totalLength(), "Zero total"
        );
    }

    @Test
    void factoryRejectsMissingBackend() {
        Assertions.assertThrows(
            UnsupportedOperationException.class,
            () -> DatabaseFactory.open(Path.of("scratch")),
            "No backend yet"
        );
    }
}
