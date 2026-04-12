/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.andreoss.search4j.core.compat.differential;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper around the command-line tools of the held reference build. The
 * tool locations live in an unversioned configuration file so that no
 * committed source names them; tests skip when the configuration is
 * absent.
 *
 * @since 0.1.0
 */
final class NativeTools {

    /**
     * Configuration file location, relative to the module directory.
     */
    private static final Path CONFIG = Path
        .of("..", "scratch", "native-tools.properties");

    /**
     * Configuration with the tool directory, library path and tool names,
     * or absent when the reference build is not held.
     */
    private static final Optional<Properties> SETTINGS = NativeTools.load();

    /**
     * Utility class, no instances.
     */
    private NativeTools() {
    }

    /**
     * True when every configured tool exists and is executable.
     *
     * @return True when differential runs can start
     */
    static boolean available() {
        return NativeTools.SETTINGS.isPresent()
            && NativeTools.SETTINGS.get().stringPropertyNames().stream()
                .filter(key -> key.startsWith("tool."))
                .allMatch(NativeTools::executable);
    }

    /**
     * Runs one tool with the given arguments and returns its output lines.
     *
     * @param tool Property key suffix naming the tool
     * @param args Tool arguments
     * @return Output lines
     * @throws IOException When the tool fails or reports a nonzero exit
     */
    static List<String> run(
        final String tool,
        final List<String> args
    ) throws IOException {
        final Properties setup = NativeTools.SETTINGS.orElseThrow(
            () -> new IOException("reference tools not held")
        );
        final List<String> cmd = new ArrayList<>(args.size() + 1);
        cmd.add(NativeTools.binary(setup, tool));
        cmd.addAll(args);
        final ProcessBuilder maker = new ProcessBuilder(cmd);
        maker.redirectErrorStream(true);
        maker.environment().put(
            "LD_LIBRARY_PATH",
            setup.getProperty("library.path", "")
        );
        final Process proc = maker.start();
        final List<String> out = NativeTools.read(proc);
        final int code = NativeTools.exit(proc, tool, cmd);
        if (code != 0) {
            throw new IOException(
                String.format(
                    "tool %s failed with exit code %d: %s",
                    tool,
                    code,
                    cmd
                )
            );
        }
        return out;
    }

    /**
     * Parses a query with the reference parser and no database.
     *
     * @param query Query text
     * @param flags Parser flag name
     * @param stemmer Stemmer language name
     * @return Query description printed by the reference parser
     * @throws IOException When the tool fails
     */
    static List<String> parse(
        final String query,
        final String flags,
        final String stemmer
    ) throws IOException {
        final List<String> args = new ArrayList<>(6);
        args.add("--flags");
        args.add(flags);
        args.add("--stemmer");
        args.add(stemmer);
        args.add(query);
        return NativeTools.run("quest", args);
    }

    /**
     * Reads database statistics from the reference inspector.
     *
     * @param dbs Database directory
     * @return Statistics name to value
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
        final List<String> out =
            NativeTools.delve(dbs, List.of("-a", "-1", "-v"));
        for (final String line : out) {
            final int pos = line.indexOf("termfreq ");
            if (pos > 0 && !line.startsWith("All terms in database")) {
                map.put(
                    line.substring(0, pos).trim(),
                    Long.parseLong(
                        NativeTools.head(line.substring(pos + 9).trim())
                    )
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
            dbs,
            List.of("-t", term, "-r", Long.toString(doc), "-1", "-v")
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
            dbs,
            List.of("-V", "-r", Long.toString(doc))
        )) {
            if (line.startsWith("Values for record")) {
                NativeTools.slots(line.substring(19).trim(), map);
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
            dbs,
            List.of("-d", "-r", Long.toString(doc))
        );
        final String text;
        if (out.isEmpty() || out.get(0).startsWith("Data for record")) {
            text = out.size() > 1 ? out.get(1) : "";
        } else {
            text = out.get(0);
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
     * Runs the reference consistency checker over a database.
     *
     * @param dbs Database directory
     * @return Checker output lines
     * @throws IOException When the tool fails
     */
    static List<String> integrity(final Path dbs) throws IOException {
        return NativeTools.run(
            "check",
            List.of(dbs.toAbsolutePath().toString())
        );
    }

    /**
     * Runs the reference searcher over a database and returns matched
     * document identifiers.
     *
     * @param dbs Database directory, may be empty when parsing only
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
        args.add("--flags");
        args.add("default");
        args.add("--stemmer");
        args.add("none");
        args.add("--msize");
        args.add(Integer.toString(size));
        args.add(query);
        final List<Long> list = new ArrayList<>(size);
        boolean matches = false;
        for (final String line : NativeTools.run("quest", args)) {
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
     * Opens one table of a database in the reference inspector and
     * returns its report.
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
        final Properties setup = NativeTools.SETTINGS.orElseThrow(
            () -> new IOException("reference tools not held")
        );
        final List<String> cmd = List.of(
            NativeTools.binary(setup, "inspect"),
            "-t",
            table,
            dbs.toAbsolutePath().toString()
        );
        final ProcessBuilder maker = new ProcessBuilder(cmd);
        maker.redirectErrorStream(true);
        maker.environment().put(
            "LD_LIBRARY_PATH",
            setup.getProperty("library.path", "")
        );
        final Process proc = maker.start();
        NativeTools.reply(proc, script);
        return NativeTools.read(proc);
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
        return NativeTools.run(
            "compact",
            List.of(
                src.toAbsolutePath().toString(),
                dest.toAbsolutePath().toString()
            )
        );
    }

    private static void reply(final Process proc, final List<String> script)
        throws IOException {
        try {
            final StringBuilder text = new StringBuilder(16);
            for (final String line : script) {
                text.append(line).append('\n');
            }
            text.append("quit\n");
            proc.getOutputStream().write(
                text.toString().getBytes(StandardCharsets.UTF_8)
            );
            proc.getOutputStream().flush();
            proc.getOutputStream().close();
        } catch (final IOException exp) {
            proc.getOutputStream().close();
        }
    }

    private static void pair(final String line, final Map<String, String> map) {
        final String text;
        if (line.startsWith("number of documents")) {
            text = "doccount" + line.substring(19);
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
        final String[] words = title.split("\\s+");
        final StringBuilder out = new StringBuilder(title.length());
        out.append(words[0]);
        for (int idx = 1; idx < words.length; ++idx) {
            final String word = words[idx];
            out.append(Character.toUpperCase(word.charAt(0)))
                .append(word.substring(1));
        }
        return out.toString();
    }

    private static void slots(final String text, final Map<String, String> map) {
        for (final String slot : text.split("\\s+")) {
            final int pos = slot.indexOf(':');
            if (pos > 0) {
                map.put(
                    slot.substring(0, pos).trim(),
                    slot.substring(pos + 1).trim()
                );
            }
        }
    }

    private static int exit(final Process proc, final String tool,
        final List<String> cmd) throws IOException {
        final int code;
        try {
            if (!proc.waitFor(60L, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                throw new IOException(
                    String.format("tool %s timed out: %s", tool, cmd)
                );
            }
            code = proc.exitValue();
        } catch (final InterruptedException exp) {
            Thread.currentThread().interrupt();
            throw new IOException(
                "interrupted while waiting for the tool",
                exp
            );
        }
        return code;
    }

    private static String binary(final Properties setup, final String tool) {
        return Path.of(
            setup.getProperty("bin.dir", ""),
            setup.getProperty(String.format("tool.%s", tool))
        ).toString();
    }

    private static boolean executable(final String key) {
        return Files.isExecutable(
            Path.of(
                NativeTools.SETTINGS.orElseThrow().getProperty("bin.dir", ""),
                NativeTools.SETTINGS.orElseThrow().getProperty(key)
            )
        );
    }

    private static Optional<Properties> load() {
        final Optional<Properties> loaded;
        if (Files.isRegularFile(NativeTools.CONFIG)) {
            loaded = NativeTools.readConfig(new Properties());
        } else {
            loaded = Optional.empty();
        }
        return loaded;
    }

    private static Optional<Properties> readConfig(final Properties setup) {
        final Optional<Properties> loaded;
        boolean read;
        try {
            NativeTools.pour(setup);
            read = true;
        } catch (final IOException exp) {
            read = false;
        }
        if (read) {
            loaded = Optional.of(setup);
        } else {
            loaded = Optional.empty();
        }
        return loaded;
    }

    private static boolean pour(final Properties setup) throws IOException {
        try (
            InputStream stream = Files.newInputStream(NativeTools.CONFIG)
        ) {
            setup.load(stream);
        }
        return true;
    }

    private static List<String> read(final Process proc) throws IOException {
        final List<String> out = new ArrayList<>(16);
        try (
            InputStream stream = proc.getInputStream();
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
            )
        ) {
            String line = reader.readLine();
            while (line != null) {
                out.add(line);
                line = reader.readLine();
            }
        }
        return out;
    }

    private static List<String> delve(
        final Path dbs,
        final List<String> opts
    ) throws IOException {
        final List<String> args = new ArrayList<>(opts.size() + 1);
        args.addAll(opts);
        args.add(dbs.toAbsolutePath().toString());
        return NativeTools.run("delve", args);
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
