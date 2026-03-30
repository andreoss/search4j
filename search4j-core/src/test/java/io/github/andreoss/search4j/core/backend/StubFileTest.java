/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the backend contract and support types.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.DatabaseNotFoundError;
import io.github.andreoss.search4j.core.types.Backend;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests of {@link StubFile}.
 *
 * @since 0.1.0
 */
final class StubFileTest {

    @Test
    void skipsBlankAndCommentLines(@TempDir final Path dir) throws IOException {
        Assertions.assertEquals(
            List.of(StubMemory.INSTANCE),
            StubFileTest.read(dir, "", "# comment", "inmemory", ""),
            "Only the entry"
        );
    }

    @Test
    void resolvesRelativePath(@TempDir final Path dir) throws IOException {
        Assertions.assertEquals(
            List.of(new StubLocal(dir.resolve("db"), Optional.empty())),
            StubFileTest.read(dir, "auto db"),
            "Relative to the stub"
        );
    }

    @Test
    void keepsAbsolutePath(@TempDir final Path dir) throws IOException {
        final Path target = dir.resolve("elsewhere").toAbsolutePath();
        Assertions.assertEquals(
            List.of(new StubLocal(target, Optional.of(Backend.VIDRIO))),
            StubFileTest.read(dir, String.format("vidrio %s", target)),
            "Absolute kept"
        );
    }

    @Test
    void readsEveryLineInOrder(@TempDir final Path dir) throws IOException {
        Assertions.assertEquals(
            List.of(
                new StubProgram("prog", ""),
                new StubProgram("prog", "a b"),
                new StubTcp("host", 1234),
                new StubTcp("::1", 80),
                new StubTcp("h", 65_535)
            ),
            StubFileTest.read(
                dir,
                "remote :prog",
                "remote :prog a b",
                "remote host:1234",
                "remote [::1]:80",
                "remote h:65535"
            ),
            "Remote entries"
        );
    }

    @Test
    void rejectsBadLines(@TempDir final Path dir) throws IOException {
        for (final String bad : new String[] {
            "bad line here",
            "bad",
            "remote",
            "remote host",
            "remote host:",
            "remote host:65536",
            "remote host:12x",
            "remote host:123456",
            "remote [::1]",
            "inmemory extra",
            "Inmemory",
        }) {
            final StubFile stub = StubFileTest.stub(dir, "inmemory", bad);
            String message = "";
            try {
                stub.entries();
            } catch (final DatabaseCorruptError ex) {
                message = ex.getMessage();
            }
            Assertions.assertTrue(
                message.endsWith("stub:2: Bad line"),
                String.format("Rejected: %s", bad)
            );
        }
    }

    @Test
    void acceptsBareVidrio(@TempDir final Path dir) throws IOException {
        Assertions.assertEquals(
            List.of(
                new StubLocal(dir, Optional.of(Backend.VIDRIO)),
                new StubLocal(dir, Optional.of(Backend.VIDRIO))
            ),
            StubFileTest.read(dir, "vidrio", "vidrio "),
            "Stub directory"
        );
    }

    @Test
    void reportsMissingFile(@TempDir final Path dir) {
        Throwable cause = null;
        try {
            new StubFile(dir.resolve("absent")).entries();
        } catch (final DatabaseNotFoundError ex) {
            cause = ex.getCause();
        }
        Assertions.assertInstanceOf(IOException.class, cause, "Cause kept");
    }

    private static List<StubEntry> read(final Path dir, final String... lines)
        throws IOException {
        return StubFileTest.stub(dir, lines).entries();
    }

    private static StubFile stub(final Path dir, final String... lines)
        throws IOException {
        final Path file = dir.resolve("stub");
        Files.write(file, List.of(lines), StandardCharsets.UTF_8);
        return new StubFile(file);
    }
}
