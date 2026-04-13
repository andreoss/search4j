/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.andreoss.search4j.core.compat.differential;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads reference tool output into the values compared with this
 * project's own: statistics, terms, postings, positions, values, document
 * data, table reports and search results.
 *
 * @since 0.1.0
 */
final class NativeTools {

    /**
     * Line holding the values of one document.
     */
    private static final String VALUES = "Values for record #";

    /**
     * Runs of white space.
     */
    private static final Pattern SPACES = Pattern.compile("\\s+");

    /**
     * Line holding document counts under another name.
     */
    private static final String COUNT = "number of documents";

    /**
     * Label before a query description printed by the reference parser.
     */
    private static final String PARSED = "Parsed Query: ";

    /**
     * Utility class, no instances.
     */
    private NativeTools() {
    }

    /**
     * True when the reference tools are held and executable.
     *
     * @return True when differential runs can start
     */
    static boolean available() {
        return ReferenceTools.available();
    }

    /**
     * Parses a query with the reference parser and no database.
     *
     * @param query Query text
     * @param options Flag names separated by commas, stemmer language,
     *  stemming strategy and default operator, in this order
     * @return Query description, or the error the reference parser printed
     * @throws IOException When the tool cannot run
     */
    static String parse(
        final String query,
        final String... options
    ) throws IOException {
        final List<String> out = ReferenceTools.capture(
            "quest",
            List.of(
                "--flags", options[0], "--stemmer", options[1],
                "--stem-strategy", options[2], "--default-op", options[3],
                "--", query
            )
        );
        if (out.isEmpty()) {
            throw new IOException(String.format("no output for %s", query));
        }
        String line = out.get(0);
        if (line.startsWith(NativeTools.PARSED)) {
            line = line.substring(NativeTools.PARSED.length());
        }
        return line;
    }

    /**
     * Reads database statistics from the reference inspector.
     *
     * @param dbs Database directory
     * @return Statistics name to value, names in camel case
     * @throws IOException When the tool fails
     */
    static Map<String, String> stats(final Path dbs) throws IOException {
        final Map<String, String> map = new LinkedHashMap<>(0);
        for (final String line : NativeTools.delve(dbs, List.of("-v"))) {
            NativeTools.pair(line, map);
        }
        return map;
    }

    /**
     * Reads every term with its document frequency.
     *
     * @param dbs Database directory
     * @return Term to document frequency
     * @throws IOException When the tool fails
     */
    static Map<String, Long> termFreqs(final Path dbs) throws IOException {
        final Map<String, Long> map = new LinkedHashMap<>(0);
        boolean listed = false;
        for (final String line : NativeTools.delve(
            dbs, List.of("-a", "-1", "-v")
        )) {
            final int pos = line.lastIndexOf(' ');
            if (line.startsWith("All terms in database")) {
                listed = true;
            } else if (listed && pos > 0) {
                map.put(
                    line.substring(0, pos),
                    Long.parseLong(line.substring(pos + 1))
                );
            }
        }
        return map;
    }

    /**
     * Reads the posting list of one term.
     *
     * @param dbs Database directory
     * @param term Term to read
     * @return Raw posting lines
     * @throws IOException When the tool fails
     */
    static List<String> postings(
        final Path dbs,
        final String term
    ) throws IOException {
        return NativeTools.delve(dbs, List.of("-t", term, "-1", "-v"));
    }

    /**
     * Reads the positions of one term in one document.
     *
     * @param dbs Database directory
     * @param term Term to read
     * @param doc Document identifier
     * @return Position lines
     * @throws IOException When the tool fails
     */
    static List<Long> positions(
        final Path dbs,
        final String term,
        final long doc
    ) throws IOException {
        final List<Long> list = new ArrayList<>(8);
        for (final String line : NativeTools.delve(
            dbs, List.of("-t", term, "-r", Long.toString(doc), "-1", "-v")
        )) {
            if (line.matches("\\d+.*")) {
                list.add(Long.parseLong(NativeTools.head(line)));
            }
        }
        return list;
    }

    /**
     * Reads the stored values of one document.
     *
     * @param dbs Database directory
     * @param doc Document identifier
     * @return Value slot to value text
     * @throws IOException When the tool fails
     */
    static Map<String, String> values(
        final Path dbs,
        final long doc
    ) throws IOException {
        final Map<String, String> map = new LinkedHashMap<>(0);
        for (final String line : NativeTools.delve(
            dbs, List.of("-V", "-r", Long.toString(doc))
        )) {
            if (line.startsWith(NativeTools.VALUES)) {
                NativeTools.slots(line.substring(NativeTools.VALUES.length()), map);
            }
        }
        return map;
    }

    /**
     * Reads the document data of one document.
     *
     * @param dbs Database directory
     * @param doc Document identifier
     * @return Document data text, if present
     * @throws IOException When the tool fails
     */
    static Optional<String> data(
        final Path dbs,
        final long doc
    ) throws IOException {
        final List<String> out = NativeTools.delve(
            dbs, List.of("-d", "-r", Long.toString(doc))
        );
        String text = "";
        if (!out.isEmpty()) {
            final String first = out.get(0);
            final int skip;
            if (first.startsWith("Data for record #")) {
                skip = 1;
            } else {
                skip = 0;
            }
            if (out.size() > skip) {
                text = out.get(skip);
            }
        }
        final Optional<String> found;
        if (text.isEmpty()) {
            found = Optional.empty();
        } else {
            found = Optional.of(text);
        }
        return found;
    }

    /**
     * Runs the reference searcher over a database and returns matched
     * document identifiers.
     *
     * @param dbs Database directory
     * @param query Query text
     * @param size Maximum number of matches
     * @return Matched document identifiers in rank order
     * @throws IOException When the tool fails
     */
    static List<Long> search(
        final Path dbs,
        final String query,
        final int size
    ) throws IOException {
        final List<String> args = new ArrayList<>(8);
        if (!dbs.toString().isEmpty()) {
            args.add("--db");
            args.add(dbs.toAbsolutePath().toString());
        }
        args.addAll(
            List.of(
                "--flags", "default", "--stemmer", "none",
                "--msize", Integer.toString(size), query
            )
        );
        final List<Long> list = new ArrayList<>(size);
        boolean matches = false;
        for (final String line : ReferenceTools.run("quest", args)) {
            if (line.startsWith("MSet:")) {
                matches = true;
            } else if (matches && line.matches("\\d+:.*")) {
                list.add(
                    Long.parseLong(
                        NativeTools.head(line.substring(0, line.indexOf(':')))
                    )
                );
            }
        }
        return list;
    }

    /**
     * Opens one table of a database in the reference inspector.
     *
     * @param dbs Database directory
     * @param table Table name
     * @return Inspector output lines
     * @throws IOException When the tool fails
     */
    static List<String> inspect(
        final Path dbs,
        final String table
    ) throws IOException {
        return NativeTools.inspect(dbs, table, List.of());
    }

    /**
     * Opens one table of a database in the reference inspector, runs the
     * given commands and returns its report.
     *
     * @param dbs Database directory
     * @param table Table name
     * @param script Inspector commands, one per line
     * @return Inspector output lines
     * @throws IOException When the tool fails
     */
    static List<String> inspect(
        final Path dbs,
        final String table,
        final List<String> script
    ) throws IOException {
        return ReferenceTools.talk(
            "inspect",
            List.of("-t", table, dbs.toAbsolutePath().toString()),
            script
        );
    }

    /**
     * Compacts a database with the reference compactor.
     *
     * @param src Source database directory
     * @param dest Destination database directory
     * @return Compactor output lines
     * @throws IOException When the tool fails
     */
    static List<String> compact(
        final Path src,
        final Path dest
    ) throws IOException {
        return ReferenceTools.run(
            "compact",
            List.of(
                src.toAbsolutePath().toString(),
                dest.toAbsolutePath().toString()
            )
        );
    }

    private static void pair(final String line, final Map<String, String> map) {
        final String text;
        if (line.startsWith(NativeTools.COUNT)) {
            text = String.format(
                "doccount%s", line.substring(NativeTools.COUNT.length())
            );
        } else {
            text = line;
        }
        final int pos = text.indexOf('=');
        if (pos > 0) {
            map.put(
                NativeTools.slug(text.substring(0, pos).trim()),
                text.substring(pos + 1).trim()
            );
        }
    }

    private static String slug(final String title) {
        final List<String> words = NativeTools.words(title);
        final StringBuilder out = new StringBuilder(title.length());
        out.append(words.get(0));
        for (int idx = 1; idx < words.size(); ++idx) {
            final String word = words.get(idx);
            out.append(Character.toUpperCase(word.charAt(0)))
                .append(word.substring(1));
        }
        return out.toString();
    }

    private static void slots(final String text, final Map<String, String> map) {
        for (final String slot : NativeTools.words(text)) {
            final int pos = slot.indexOf(':');
            if (pos > 0) {
                map.put(
                    slot.substring(0, pos), slot.substring(pos + 1)
                );
            }
        }
    }

    private static List<String> words(final String text) {
        return new ArrayList<>(List.of(NativeTools.SPACES.split(text.trim())));
    }

    private static List<String> delve(
        final Path dbs,
        final List<String> opts
    ) throws IOException {
        final List<String> args = new ArrayList<>(opts.size() + 1);
        args.addAll(opts);
        args.add(dbs.toAbsolutePath().toString());
        return ReferenceTools.run("delve", args);
    }

    private static String head(final String text) {
        final String first;
        final int pos = text.indexOf(' ');
        if (pos < 0) {
            first = text;
        } else {
            first = text.substring(0, pos);
        }
        return first;
    }
}
