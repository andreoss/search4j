/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.DatabaseNotFoundError;
import io.github.andreoss.search4j.core.types.Backend;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stub database file: one database per line, as a type word and a target.
 *
 * <p>Blank lines and lines starting with a hash are skipped. A type word is
 * {@code auto} or {@code vidrio} with a path, {@code remote} with
 * {@code :program args} or {@code host:port}, or {@code inmemory} alone; any
 * other line raises a database-corrupt error naming the line.</p>
 *
 * @since 0.1.0
 */
public final class StubFile {

    /**
     * The stub file.
     */
    private final Path file;

    /**
     * Creates a reader of a stub file.
     *
     * @param file Stub file location
     */
    public StubFile(final Path file) {
        this.file = file;
    }

    /**
     * Reads and parses every line.
     *
     * <p>An unreadable file raises a database-not-found error.</p>
     *
     * @return Databases in file order
     */
    public List<StubEntry> entries() {
        final List<String> lines;
        try {
            lines = Files.readAllLines(this.file, StandardCharsets.UTF_8);
        } catch (final IOException ex) {
            final DatabaseNotFoundError error = new DatabaseNotFoundError(
                String.format("Couldn't open stub database file: %s", this.file),
                ex.getMessage()
            );
            error.initCause(ex);
            throw error;
        }
        final List<StubEntry> result = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index += 1) {
            final String line = lines.get(index);
            final int number = index + 1;
            if (!line.isEmpty() && line.charAt(0) != '#') {
                result.add(
                    this.entry(line).orElseThrow(
                        () -> new DatabaseCorruptError(
                            String.format("%s:%d: Bad line", this.file, number)
                        )
                    )
                );
            }
        }
        return result;
    }

    private Optional<StubEntry> entry(final String line) {
        final int space = line.indexOf(' ');
        String type = line;
        String rest = "";
        if (space >= 0) {
            type = line.substring(0, space);
            rest = line.substring(space + 1);
        }
        Optional<StubEntry> result = Optional.empty();
        if ("auto".equals(type)) {
            result = Optional.of(new StubLocal(this.resolve(rest), Optional.empty()));
        } else if ("vidrio".equals(type)) {
            result = Optional.of(
                new StubLocal(this.resolve(rest), Optional.of(Backend.VIDRIO))
            );
        } else if ("remote".equals(type) && !rest.isEmpty()) {
            result = StubFile.remote(rest);
        } else if ("inmemory".equals(type) && rest.isEmpty()) {
            result = Optional.of(StubMemory.INSTANCE);
        }
        return result;
    }

    private Path resolve(final String target) {
        Path result = Path.of(target);
        final Path dir = this.file.getParent();
        if (!result.isAbsolute() && dir != null) {
            result = dir.resolve(result);
        }
        return result;
    }

    private static Optional<StubEntry> remote(final String target) {
        final Optional<StubEntry> result;
        if (target.charAt(0) == ':') {
            final int space = target.indexOf(' ');
            if (space < 0) {
                result = Optional.of(new StubProgram(target.substring(1), ""));
            } else {
                result = Optional.of(
                    new StubProgram(
                        target.substring(1, space), target.substring(space + 1)
                    )
                );
            }
        } else {
            result = StubFile.tcp(target);
        }
        return result;
    }

    private static Optional<StubEntry> tcp(final String target) {
        final int colon = target.lastIndexOf(':');
        Optional<StubEntry> result = Optional.empty();
        if (colon >= 0 && !StubFile.bracketed(target)) {
            final String digits = target.substring(colon + 1);
            if (digits.matches("[0-9]{1,5}")
                && Integer.parseInt(digits) <= 65_535) {
                result = Optional.of(
                    new StubTcp(
                        StubFile.unbracketed(target.substring(0, colon)),
                        Integer.parseInt(digits)
                    )
                );
            }
        }
        return result;
    }

    private static String unbracketed(final String host) {
        String result = host;
        if (StubFile.bracketed(host)) {
            result = host.substring(1, host.length() - 1);
        }
        return result;
    }

    private static boolean bracketed(final String text) {
        return text.length() >= 2 && text.charAt(0) == '['
            && text.charAt(text.length() - 1) == ']';
    }
}
