/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.andreoss.search4j.core.compat.differential;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes databases with the reference library and judges databases with
 * the reference checker and reader, so that each oracle can run over a
 * reference database as well as over one written by this project.
 *
 * @since 0.5.0
 */
final class ReferenceDatabases {

    /**
     * Property key suffix of the reference writer.
     */
    private static final String WRITER = "write";

    /**
     * Collection frequency in a posting list header.
     */
    private static final Pattern COLLECTION = Pattern
        .compile("collfreq (\\d+)");

    /**
     * Utility class, no instances.
     */
    private ReferenceDatabases() {
    }

    /**
     * True when the reference writer, which builds the database of each
     * differential case with the reference library, is configured.
     *
     * @return True when reference databases can be written
     */
    static boolean writable() {
        return ReferenceTools.configured(ReferenceDatabases.WRITER);
    }

    /**
     * Writes the database of one differential case with the reference
     * library.
     *
     * @param scenario Case name the reference writer knows
     * @param dbs Database directory to create
     * @throws IOException When the tool fails
     */
    static void write(final String scenario, final Path dbs)
        throws IOException {
        ReferenceTools.run(
            ReferenceDatabases.WRITER,
            List.of(scenario, dbs.toAbsolutePath().toString())
        );
    }

    /**
     * Runs the reference consistency checker over a database. The checker
     * accepts a database when it exits with status zero and its last
     * non-blank line is the success line.
     *
     * @param dbs Database directory
     * @return Nothing when accepted; otherwise the exit status and every
     *  checker output line
     * @throws IOException When the tool cannot run
     */
    static List<String> integrity(final Path dbs) throws IOException {
        final ReferenceTools.Outcome outcome = ReferenceTools.execute(
            "check", List.of(dbs.toAbsolutePath().toString())
        );
        final List<String> lines = outcome.lines().stream()
            .filter(line -> !line.isBlank())
            .toList();
        final List<String> faults = new ArrayList<>(lines.size() + 1);
        if (outcome.code() != 0 || lines.isEmpty()
            || !"No errors found".equals(lines.get(lines.size() - 1))) {
            faults.add(String.format("exit code %d", outcome.code()));
            faults.addAll(outcome.lines());
        }
        return faults;
    }

    /**
     * Reads the collection frequency of one term from its posting list
     * header.
     *
     * @param dbs Database directory
     * @param term Term to read
     * @return Collection frequency, if the term is present
     * @throws IOException When the tool fails
     */
    static Optional<Long> collectionFreq(
        final Path dbs,
        final String term
    ) throws IOException {
        Optional<Long> found = Optional.empty();
        for (final String line : NativeTools.postings(dbs, term)) {
            final Matcher match = ReferenceDatabases.COLLECTION.matcher(line);
            if (found.isEmpty() && match.find()) {
                found = Optional.of(Long.parseLong(match.group(1)));
            }
        }
        return found;
    }
}
