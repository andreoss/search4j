/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Loading of the reference-produced fixtures held by this project.
 */
package io.github.andreoss.search4j.core.compat;

import io.github.andreoss.search4j.core.backend.vidrio.BlockNumber;
import io.github.andreoss.search4j.core.backend.vidrio.BlockSize;
import io.github.andreoss.search4j.core.backend.vidrio.RevisionNumber;
import io.github.andreoss.search4j.core.backend.vidrio.TableRoot;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Access to the fixtures produced by the reference implementation, kept
 * under the test resources of the vidrio package.
 *
 * @since 0.1.0
 */
final class ReferenceFixtures {

    /**
     * Class path prefix of the fixture directory.
     */
    private static final String BASE = "/io/github/andreoss/search4j/core/backend/vidrio/";

    /**
     * Revision the reference database was committed at.
     */
    private static final RevisionNumber REVISION = new RevisionNumber(1L);

    /**
     * Utility class, no instances.
     */
    private ReferenceFixtures() {
    }

    /**
     * Bytes of a fixture.
     *
     * @param name Fixture name without its extension
     * @return Whole content
     * @throws IOException When the fixture cannot be read
     */
    static byte[] bytes(final String name) throws IOException {
        final byte[] result;
        try (InputStream stream = ReferenceFixtures.stream(name)) {
            result = stream.readAllBytes();
        }
        return result;
    }

    /**
     * Path of a fixture.
     *
     * @param name Fixture name without its extension
     * @return Path in the test class output
     * @throws URISyntaxException When the resource location is not a path
     */
    static Path path(final String name) throws URISyntaxException {
        final URL found = ReferenceFixtures.class.getResource(
            String.format("%s%s.bin", ReferenceFixtures.BASE, name)
        );
        if (found == null) {
            throw new IllegalArgumentException(
                String.format("No such fixture: %s", name)
            );
        }
        return Path.of(found.toURI());
    }

    /**
     * Names of every binary fixture held by the test resources.
     *
     * @return Fixture names without extensions
     * @throws IOException When a directory entry cannot be read
     * @throws URISyntaxException When the resource directory is not a path
     */
    static List<String> names() throws IOException, URISyntaxException {
        final URL found = ReferenceFixtures.class.getResource(
            ReferenceFixtures.BASE
        );
        if (found == null) {
            throw new IllegalArgumentException("No fixture directory");
        }
        final Path directory = Path.of(found.toURI());
        final List<String> result;
        try (Stream<Path> files = Files.walk(directory)) {
            result = files.filter(Files::isRegularFile)
                .filter(file -> file.toString().endsWith(".bin"))
                .map(directory::relativize)
                .map(file -> file.toString().replace('\\', '/'))
                .map(file -> file.substring(0, file.length() - 4))
                .sorted()
                .toList();
        }
        return result;
    }

    /**
     * Root of every reference table: block zero, leaf level, one revision.
     *
     * @return Root of the reference tables
     */
    static TableRoot root() {
        return new TableRoot(
            new BlockNumber(0L), 0, BlockSize.MIN,
            ReferenceFixtures.REVISION, false
        );
    }

    private static InputStream stream(final String name) {
        final InputStream stream = ReferenceFixtures.class.getResourceAsStream(
            String.format("%s%s.bin", ReferenceFixtures.BASE, name)
        );
        if (stream == null) {
            throw new IllegalArgumentException(
                String.format("No such fixture: %s", name)
            );
        }
        return stream;
    }
}
