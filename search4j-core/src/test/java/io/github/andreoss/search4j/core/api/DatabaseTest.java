/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API contracts.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.TermCount;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link DatabaseFactory}.
 *
 * @since 0.1.0
 */
final class DatabaseTest {

    @Test
    void factoryCreatesEmptyInMemoryDatabase() {
        try (WritableDatabase database = DatabaseFactory.inMemory()) {
            Assertions.assertEquals(
                TermCount.ZERO, database.docCount(), "Empty database"
            );
        }
    }

    @Test
    void factoryCreatesIndependentDatabases() {
        try (
            WritableDatabase first = DatabaseFactory.inMemory();
            WritableDatabase second = DatabaseFactory.inMemory()
        ) {
            first.setMetadata("key", "value");
            Assertions.assertEquals(
                "", second.metadata("key"), "Separate contents"
            );
        }
    }

    @Test
    void factoryRejectsPathOpening() {
        Assertions.assertThrows(
            UnsupportedOperationException.class,
            () -> DatabaseFactory.open(Path.of("scratch")),
            "No on-disk backend yet"
        );
    }

    @Test
    void factoryRejectsWritablePathOpening() {
        Assertions.assertThrows(
            UnsupportedOperationException.class,
            () -> DatabaseFactory.openWritable(Path.of("scratch"), 0),
            "No on-disk backend yet"
        );
    }
}
