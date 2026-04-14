/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.andreoss.search4j.core.compat.differential;

import io.github.andreoss.search4j.core.backend.Uuid;
import io.github.andreoss.search4j.core.backend.vidrio.VidrioFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Compares two database directories byte for byte, file by file, the
 * identifier bytes of the version file excepted, since every database
 * created gets a fresh identifier.
 *
 * @since 0.8.1
 */
final class ReferenceBytes {

    /**
     * Utility class, no instances.
     */
    private ReferenceBytes() {
    }

    /**
     * Lists every way two database directories differ.
     *
     * @param expected Directory holding the expected files
     * @param actual Directory compared with it
     * @return Nothing when both hold the same files with the same bytes;
     *  otherwise one line per difference
     * @throws IOException When a directory or file cannot be read
     */
    static List<String> differences(final Path expected, final Path actual)
        throws IOException {
        final List<String> faults = new ArrayList<>(0);
        final List<String> names = ReferenceBytes.names(expected);
        if (!names.equals(ReferenceBytes.names(actual))) {
            faults.add(
                String.format(
                    "files %s in %s, %s in %s", names, expected,
                    ReferenceBytes.names(actual), actual
                )
            );
        }
        for (final String name : names) {
            final Path other = actual.resolve(name);
            if (Files.isRegularFile(other) && !Arrays.equals(
                ReferenceBytes.content(expected.resolve(name)),
                ReferenceBytes.content(other)
            )) {
                faults.add(String.format("bytes of %s differ", other));
            }
        }
        return faults;
    }

    private static List<String> names(final Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(file -> file.getFileName().toString())
                .sorted()
                .toList();
        }
    }

    private static byte[] content(final Path file) throws IOException {
        final byte[] data = Files.readAllBytes(file);
        final int start = VidrioFormat.MAGIC_BYTES + 2;
        if (file.getFileName().toString().equals(VidrioFormat.versionFileName())
            && data.length >= start + Uuid.BYTES) {
            Arrays.fill(data, start, start + Uuid.BYTES, (byte) 0);
        }
        return data;
    }
}
