/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseLockError;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests of {@link WriteLock}.
 *
 * @since 0.1.0
 */
final class WriteLockTest {

    @Test
    void holdsUntilClosed(@TempDir final Path dir) {
        final boolean before = WriteLock.held(dir);
        final WriteLock lock = WriteLock.acquire(dir, false);
        final List<Boolean> held = List.of(lock.locked(), WriteLock.held(dir));
        lock.close();
        Assertions.assertEquals(
            List.of(false, true, true, false, false, true),
            List.of(
                before, held.get(0), held.get(1), lock.locked(),
                WriteLock.held(dir),
                Files.exists(dir.resolve(VidrioFormat.lockFileName()))
            ),
            "Lock held while open, file left behind"
        );
    }

    @Test
    void refusesSecondLock(@TempDir final Path dir) {
        try (WriteLock first = WriteLock.acquire(dir, false)) {
            Assertions.assertEquals(
                List.of(true, true),
                List.of(
                    Assertions.assertThrows(
                        DatabaseLockError.class, () -> WriteLock.acquire(dir, false),
                        "Already locked"
                    ).getMessage().endsWith(": already locked"),
                    first.locked()
                ),
                "Reference message, first lock kept"
            );
        }
    }

    @Test
    void waitsForLockWhenAsked(@TempDir final Path dir) throws Exception {
        final WriteLock first = WriteLock.acquire(dir, false);
        final CompletableFuture<WriteLock> second = CompletableFuture.supplyAsync(
            () -> WriteLock.acquire(dir, true)
        );
        final List<Boolean> states = new ArrayList<>(2);
        states.add(!second.isDone());
        first.close();
        try (WriteLock lock = second.get(30L, TimeUnit.SECONDS)) {
            states.add(lock.locked());
            Assertions.assertEquals(
                List.of(true, true), states,
                "Second lock obtained once the first was released"
            );
        }
    }

    @Test
    void reportsMissingDirectory(@TempDir final Path dir) {
        final Path missing = dir.resolve("absent");
        Assertions.assertThrows(
            DatabaseLockError.class, () -> WriteLock.acquire(missing, false),
            "Lock file cannot be created"
        );
        Assertions.assertFalse(WriteLock.held(missing), "Nothing to hold");
    }
}
