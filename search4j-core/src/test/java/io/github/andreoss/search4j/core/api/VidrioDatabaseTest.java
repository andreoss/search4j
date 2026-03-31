/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API contracts.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.DocId;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end tests of the vidrio backend against the in-memory backend
 * given the same documents.
 *
 * @since 0.1.0
 */
final class VidrioDatabaseTest {

    @Test
    void agreesWithInMemoryBackendOnEveryListing(@TempDir final Path dir) {
        try (Twins twins = Twins.create(dir.resolve("db"), 19L)) {
            for (int idx = 0; idx < 1000; ++idx) {
                twins.add();
            }
            twins.metadata("author", "someone");
            twins.metadata("zkey", "last");
            final String pending = VidrioDatabaseTest.compare(twins);
            twins.vidrio().commit();
            try (Database reopened = DatabaseFactory.open(dir.resolve("db"))) {
                Assertions.assertEquals(
                    List.of("", "", ""),
                    List.of(
                        pending, VidrioDatabaseTest.compare(twins),
                        Snapshot.difference(
                            VidrioDatabaseTest.snapshot(twins.memory()),
                            VidrioDatabaseTest.snapshot(reopened)
                        )
                    ),
                    "Pending, committed and reopened listings agree"
                );
            }
        }
    }

    @Test
    void agreesAfterReplacementsAndDeletions(@TempDir final Path dir) {
        try (Twins twins = Twins.create(dir.resolve("db"), 23L)) {
            for (int idx = 0; idx < 200; ++idx) {
                twins.add();
            }
            twins.vidrio().commit();
            for (int idx = 1; idx <= 200; idx += 3) {
                twins.replace(DocId.of(idx));
            }
            for (int idx = 2; idx <= 200; idx += 5) {
                twins.delete(DocId.of(idx));
            }
            for (int idx = 0; idx < 30; ++idx) {
                twins.add();
            }
            twins.replace(DocId.of(300));
            twins.metadata("author", "someone");
            final String first = VidrioDatabaseTest.compare(twins);
            twins.vidrio().commit();
            for (int idx = 1; idx <= 200; idx += 7) {
                twins.replace(DocId.of(idx));
            }
            for (int idx = 4; idx <= 200; idx += 10) {
                twins.delete(DocId.of(idx));
            }
            twins.metadata("author", "");
            final String second = VidrioDatabaseTest.compare(twins);
            twins.vidrio().commit();
            try (Database reopened = DatabaseFactory.open(dir.resolve("db"))) {
                Assertions.assertEquals(
                    List.of("", "", ""),
                    List.of(
                        first, second,
                        Snapshot.difference(
                            VidrioDatabaseTest.snapshot(twins.memory()),
                            VidrioDatabaseTest.snapshot(reopened)
                        )
                    ),
                    "Two rounds pending and reopened agree"
                );
            }
        }
    }

    private static String compare(final Twins twins) {
        return Snapshot.difference(
            VidrioDatabaseTest.snapshot(twins.memory()),
            VidrioDatabaseTest.snapshot(twins.vidrio())
        );
    }

    private static List<String> snapshot(final Database database) {
        return Snapshot.of(database, Twins.TERMS, 305);
    }
}
