/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.Uuid;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Digests of the files of a database, by the role of each file, the
 * identifier bytes of the version file masked.
 *
 * @since 0.9.1
 */
final class FileDigests {

    /**
     * Utility class.
     */
    private FileDigests() {
    }

    /**
     * Digests of every file of a database directory.
     *
     * @param dbs Database directory
     * @return SHA-256 in hexadecimal by table name, version or lock
     * @throws IOException If a file cannot be read
     */
    static Map<String, String> of(final Path dbs) throws IOException {
        final Map<String, String> roles = new TreeMap<>();
        for (final TableType type : TableType.values()) {
            roles.put(type.fileName(), type.tableName());
        }
        roles.put(VidrioFormat.versionFileName(), "version");
        roles.put(VidrioFormat.lockFileName(), "lock");
        final Map<String, String> found = new TreeMap<>();
        try (Stream<Path> files = Files.list(dbs)) {
            for (final Path file : files.toList()) {
                final String name = file.getFileName().toString();
                byte[] data = Files.readAllBytes(file);
                if (name.equals(VidrioFormat.versionFileName())) {
                    data = FileDigests.masked(data);
                }
                found.put(roles.getOrDefault(name, name), FileDigests.digest(data));
            }
        }
        return found;
    }

    /**
     * SHA-256 of bytes.
     *
     * @param data Bytes
     * @return Digest in hexadecimal
     */
    static String digest(final byte[] data) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(data)
            );
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static byte[] masked(final byte[] data) {
        final byte[] copy = data.clone();
        final int start = VidrioFormat.MAGIC_BYTES + 2;
        Arrays.fill(copy, start, start + Uuid.BYTES, (byte) 0);
        return copy;
    }
}
