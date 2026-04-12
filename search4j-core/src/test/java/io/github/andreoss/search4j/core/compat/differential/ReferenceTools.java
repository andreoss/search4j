/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.andreoss.search4j.core.compat.differential;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Starts the command-line tools of the held reference build. The tool
 * directory and library path come from an unversioned configuration file,
 * so no committed source names them; the tools are unavailable when the
 * file is absent. Every tool gets its whole input and a closed input
 * stream, and is destroyed when it outlives a bounded wait.
 *
 * @since 0.1.0
 */
final class ReferenceTools {

    /**
     * Configuration file location, relative to the module directory.
     */
    private static final Path CONFIG = Path
        .of("..", "scratch", "native-tools.properties");

    /**
     * Seconds a tool may run.
     */
    private static final long LIMIT = 60L;

    /**
     * Configuration with the tool directory, library path and tool names,
     * or absent when the reference build is not held.
     */
    private static final Optional<Properties> SETTINGS = ReferenceTools.load();

    /**
     * Utility class, no instances.
     */
    private ReferenceTools() {
    }

    /**
     * True when every configured tool exists and is executable.
     *
     * @return True when differential runs can start
     */
    static boolean available() {
        return ReferenceTools.SETTINGS.isPresent()
            && ReferenceTools.SETTINGS.get().stringPropertyNames().stream()
                .filter(key -> key.startsWith("tool."))
                .allMatch(ReferenceTools::executable);
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
        final Outcome outcome = ReferenceTools.launch(tool, args, List.of());
        if (outcome.code() != 0) {
            throw new IOException(
                String.format(
                    "tool %s failed with exit code %d: %s",
                    tool, outcome.code(), args
                )
            );
        }
        return outcome.lines();
    }

    /**
     * Runs one tool and returns its output lines whatever its exit status,
     * for tools that report rejected input through it.
     *
     * @param tool Property key suffix naming the tool
     * @param args Tool arguments
     * @return Output lines
     * @throws IOException When the tool cannot run or times out
     */
    static List<String> capture(
        final String tool,
        final List<String> args
    ) throws IOException {
        return ReferenceTools.launch(tool, args, List.of()).lines();
    }

    /**
     * Runs a conversational tool: the script and a final quit command are
     * written to its input, which is then closed, and the report read
     * back. A failing exit status is not reported, because the tool exits
     * nonzero when the last command is an unknown table.
     *
     * @param tool Property key suffix naming the tool
     * @param args Tool arguments
     * @param script Commands, one per line
     * @return Output lines
     * @throws IOException When the tool cannot run or times out
     */
    static List<String> talk(
        final String tool,
        final List<String> args,
        final List<String> script
    ) throws IOException {
        final List<String> input = new ArrayList<>(script);
        input.add("quit");
        return ReferenceTools.launch(tool, args, input).lines();
    }

    private static Outcome launch(
        final String tool,
        final List<String> args,
        final List<String> input
    ) throws IOException {
        final Properties setup = ReferenceTools.SETTINGS.orElseThrow(
            () -> new IOException("reference tools not held")
        );
        final List<String> cmd = new ArrayList<>(args.size() + 1);
        cmd.add(ReferenceTools.binary(setup, tool));
        cmd.addAll(args);
        final ProcessBuilder maker = new ProcessBuilder(cmd);
        maker.redirectErrorStream(true);
        maker.environment().put(
            "LD_LIBRARY_PATH",
            setup.getProperty("library.path", "")
        );
        final Process proc = maker.start();
        final CompletableFuture<List<String>> output =
            CompletableFuture.supplyAsync(() -> ReferenceTools.drain(proc));
        ReferenceTools.feed(proc, input);
        return new Outcome(
            ReferenceTools.exit(proc, tool, cmd),
            ReferenceTools.collect(output, tool)
        );
    }

    private static void feed(final Process proc, final List<String> input)
        throws IOException {
        final StringBuilder text = new StringBuilder(16);
        for (final String line : input) {
            text.append(line).append('\n');
        }
        try (OutputStream sink = proc.getOutputStream()) {
            sink.write(text.toString().getBytes(StandardCharsets.UTF_8));
        } catch (final IOException exp) {
            if (proc.isAlive()) {
                proc.destroyForcibly();
                throw exp;
            }
        }
    }

    private static List<String> collect(
        final CompletableFuture<List<String>> output,
        final String tool
    ) throws IOException {
        try {
            return output.get(ReferenceTools.LIMIT, TimeUnit.SECONDS);
        } catch (final InterruptedException exp) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while reading the tool", exp);
        } catch (final ExecutionException | TimeoutException exp) {
            throw new IOException(
                String.format("output of tool %s unreadable", tool), exp
            );
        }
    }

    private static int exit(final Process proc, final String tool,
        final List<String> cmd) throws IOException {
        final int code;
        try {
            if (!proc.waitFor(ReferenceTools.LIMIT, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                throw new IOException(
                    String.format("tool %s timed out: %s", tool, cmd)
                );
            }
            code = proc.exitValue();
        } catch (final InterruptedException exp) {
            proc.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException(
                "interrupted while waiting for the tool", exp
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
                ReferenceTools.SETTINGS.orElseThrow()
                    .getProperty("bin.dir", ""),
                ReferenceTools.SETTINGS.orElseThrow().getProperty(key)
            )
        );
    }

    private static Optional<Properties> load() {
        final Optional<Properties> loaded;
        if (Files.isRegularFile(ReferenceTools.CONFIG)) {
            loaded = ReferenceTools.readConfig(new Properties());
        } else {
            loaded = Optional.empty();
        }
        return loaded;
    }

    private static Optional<Properties> readConfig(final Properties setup) {
        final Optional<Properties> loaded;
        boolean read;
        try {
            ReferenceTools.pour(setup);
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
            InputStream stream = Files.newInputStream(ReferenceTools.CONFIG)
        ) {
            setup.load(stream);
        }
        return true;
    }

    private static List<String> drain(final Process proc) {
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
        } catch (final IOException exp) {
            throw new UncheckedIOException(exp);
        }
        return out;
    }

    /**
     * Exit status and output of one tool run.
     *
     * @param code Exit status
     * @param lines Output lines
     * @since 0.1.0
     */
    private record Outcome(int code, List<String> lines) {
    }
}
