/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.backend.list.TermWeight;
import io.github.andreoss.search4j.core.error.SearchError;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TotalLength;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Replays vectors captured from the reference library: each line names its
 * kind, the scheme and the inputs, and ends with the reference output.
 *
 * @since 0.1.0
 */
final class WeightVectors {

    /**
     * Captured vector field delimiter.
     */
    private static final Pattern TAB = Pattern.compile("\\t");

    /**
     * Term delimiter of the fixtures.
     */
    private static final Pattern SPACE = Pattern.compile(" ");

    /**
     * Column of the first statistic in part vectors.
     */
    private static final int STATS = 2;

    /**
     * Schemes by specification.
     */
    private final Map<String, Weight> schemes;

    /**
     * Failures found.
     */
    private final List<String> failures;

    /**
     * Checks by vector kind.
     */
    private final Map<String, Consumer<String[]>> checks;

    /**
     * Specifications replayed.
     */
    private final Predicate<String> specs;

    /**
     * Number of rows replayed.
     */
    private int replayed;

    /**
     * Creates a replay with no failures.
     */
    WeightVectors() {
        this(spec -> true);
    }

    /**
     * Creates a replay of the vectors of some schemes.
     *
     * @param specs Specifications replayed
     */
    WeightVectors(final Predicate<String> specs) {
        this.schemes = new HashMap<>(64);
        this.failures = new ArrayList<>(0);
        this.checks = new HashMap<>(16);
        this.specs = specs;
    }

    /**
     * Replays every vector of the given kinds.
     *
     * @param kinds Letters of the kinds to replay
     * @return Failures, empty when every vector matches
     * @throws IOException When the vectors cannot be read
     */
    List<String> check(final String kinds) throws IOException {
        return this.check(kinds, "weight-vectors.tsv");
    }

    /**
     * Replays every vector of the given kinds held in a fixture.
     *
     * @param kinds Letters of the kinds to replay
     * @param name Resource name of the vectors
     * @return Failures, empty when every vector matches
     * @throws IOException When the vectors cannot be read
     */
    List<String> check(final String kinds, final String name)
        throws IOException {
        this.register();
        final Map<String, List<String[]>> docs = new HashMap<>(64);
        for (final String[] row : WeightVectors.rows(name)) {
            if (kinds.contains(row[0]) && this.specs.test(row[1])) {
                this.replayed = this.replayed + 1;
                if ("D".equals(row[0])) {
                    docs.computeIfAbsent(
                        String.join("\t", row[1], row[2]),
                        key -> new ArrayList<>(8)
                    ).add(row);
                } else {
                    this.checks.get(row[0]).accept(row);
                }
            }
        }
        for (final List<String[]> expected : docs.values()) {
            this.ranked(expected);
        }
        return this.failures;
    }

    /**
     * Number of rows replayed by the last check.
     *
     * @return Rows of the replayed kinds and schemes
     */
    int replayed() {
        return this.replayed;
    }

    /**
     * Reads a fixture of this package.
     *
     * @param name Resource name
     * @return Rows split at tabs
     * @throws IOException When the fixture cannot be read
     */
    static List<String[]> rows(final String name) throws IOException {
        final List<String[]> rows = new ArrayList<>(16_384);
        try (
            BufferedReader input = new BufferedReader(
                new InputStreamReader(
                    WeightVectors.class.getResourceAsStream(name),
                    StandardCharsets.UTF_8
                )
            )
        ) {
            String line = input.readLine();
            while (line != null) {
                rows.add(WeightVectors.TAB.split(line, -1));
                line = input.readLine();
            }
        }
        return rows;
    }

    /**
     * Tests a computed weight against a captured one: within a relative
     * 1e-9 when finite, the same non-finite value otherwise.
     *
     * @param text Captured value, as the reference prints it
     * @param actual Computed value
     * @return True when they agree
     */
    static boolean near(final String text, final double actual) {
        final double expected = WeightVectors.number(text);
        final boolean result;
        if (Double.isFinite(expected)) {
            result = Double.isFinite(actual)
                && Math.abs(expected - actual)
                <= 1e-9 * Math.max(Math.abs(expected), Math.abs(actual));
        } else {
            result = Double.compare(expected, actual) == 0;
        }
        return result;
    }

    /**
     * Creates a scheme from its specification: parameters text, or a name
     * and serialised parameters after an at sign.
     *
     * @param spec Specification
     * @return The scheme
     */
    static Weight parse(final String spec) {
        final Weight weight;
        if (spec.charAt(0) == '@') {
            final String[] parts = WeightVectors.SPACE.split(spec, -1);
            weight = new Registry().getWeightingScheme(parts[0].substring(1))
                .unserialise(HexFormat.of().parseHex(parts[1]));
        } else {
            weight = Weight.create(spec, new Registry());
        }
        return weight;
    }

    private void register() {
        this.checks.put(
            "S",
            row -> this.close(
                row[21],
                this.part(row).sumPart(
                    WeightVectors.count(row[17]), WeightVectors.doc(row, 18)
                ),
                row
            )
        );
        this.checks.put(
            "M", row -> this.close(row[17], this.part(row).maxPart(), row)
        );
        this.checks.put(
            "E",
            row -> this.close(
                row[20],
                this.scheme(row[1]).extra(WeightVectors.stats(row))
                    .sumExtra(WeightVectors.doc(row, 17)),
                row
            )
        );
        this.checks.put(
            "X",
            row -> this.close(
                row[17],
                this.scheme(row[1]).extra(WeightVectors.stats(row)).maxExtra(),
                row
            )
        );
        this.checks.put("C", this::size);
        this.checks.put("P", this::created);
        this.checks.put("R", this::rejected);
        this.checks.put("U", this::unserialised);
        this.checks.put("V", this::refused);
        this.checks.put("G", this::looked);
    }

    private TermWeight part(final String... row) {
        return this.scheme(row[1]).term(
            WeightVectors.stats(row), Double.parseDouble(row[WeightVectors.STATS])
        );
    }

    private void close(final String text, final double actual, final String... row) {
        if (!WeightVectors.near(text, actual)) {
            this.failures.add(
                String.format(
                    "%s expected %s got %s", String.join(" ", row), text, actual
                )
            );
        }
    }

    private void created(final String... row) {
        final Weight weight = this.scheme(row[1]);
        this.same(
            String.join("\t", row[2], row[3]),
            String.join(
                "\t", weight.name(), HexFormat.of().formatHex(weight.serialise())
            ),
            row
        );
    }

    private void rejected(final String... row) {
        this.same(
            String.join("\t", row[2], row[3]),
            WeightVectors.failure(() -> Weight.create(row[1], new Registry()), true),
            row
        );
    }

    private void unserialised(final String... row) {
        final Weight weight = new Registry().getWeightingScheme(row[1])
            .unserialise(HexFormat.of().parseHex(row[2]));
        this.same(
            String.join("\t", row[3], row[4]),
            String.join(
                "\t", weight.name(), HexFormat.of().formatHex(weight.serialise())
            ),
            row
        );
    }

    private void refused(final String... row) {
        this.same(
            row[3],
            WeightVectors.failure(
                () -> new Registry().getWeightingScheme(row[1])
                    .unserialise(HexFormat.of().parseHex(row[2])),
                false
            ),
            row
        );
    }

    private void looked(final String... row) {
        final Registry registry = new Registry();
        String found = "missing";
        if (registry.hasWeightingScheme(row[1])) {
            found = registry.getWeightingScheme(row[1]).name();
        }
        this.same(row[2], found, row);
    }

    private void size(final String... row) {
        this.same(
            row[3],
            Integer.toString(
                WeightFixture.search(row[2], this.scheme(row[1])).toList().size()
            ),
            row
        );
    }

    private void same(final String expected, final String actual, final String... row) {
        if (!expected.equals(actual)) {
            this.failures.add(
                String.format("%s got %s", String.join(" ", row), actual)
            );
        }
    }

    private void ranked(final List<String[]> expected) {
        final String[] head = expected.get(0);
        final List<MSet.Item> items = WeightFixture.search(head[2], this.scheme(head[1])).toList();
        for (final String[] row : expected) {
            final int rank = Integer.parseInt(row[3]);
            if (rank >= items.size()
                || items.get(rank).docId().value() != Integer.parseInt(row[4])) {
                this.failures.add(
                    String.format(
                        "%s got %s", String.join(" ", row), WeightVectors.text(items)
                    )
                );
            } else {
                this.close(row[5], items.get(rank).weight(), row);
            }
        }
    }

    private Weight scheme(final String spec) {
        return this.schemes.computeIfAbsent(spec, WeightVectors::parse);
    }

    private static String text(final List<MSet.Item> items) {
        final StringBuilder out = new StringBuilder(64);
        for (final MSet.Item item : items) {
            out.append(item.docId().value()).append(':')
                .append(item.weight()).append(' ');
        }
        return out.toString();
    }

    private static String failure(final Runnable action, final boolean message) {
        String result = "accepted";
        try {
            action.run();
        } catch (final SearchError ex) {
            result = ex.getClass().getSimpleName();
            if (message) {
                result = String.join("\t", result, ex.getMessage());
            }
        }
        return result;
    }

    private static WeightStats stats(final String... row) {
        final int base = WeightVectors.STATS;
        final long size = Long.parseLong(row[base + 1]);
        long total = Long.parseLong(row[base + 3]);
        if (total == 0L) {
            total = Math.round(Double.parseDouble(row[base + 4]) * size);
        }
        return new WeightStats(
            new CollectionStats(
                new TermCount(size), WeightVectors.count(row[base + 2]),
                new TotalLength(total), WeightVectors.count(row[base + 5]),
                new LengthBounds(
                    WeightVectors.count(row[base + 6]),
                    WeightVectors.count(row[base + 7]),
                    WeightVectors.count(row[base + 8])
                )
            ),
            new TermStats(
                WeightVectors.count(row[base + 10]),
                WeightVectors.count(row[base + 11]),
                WeightVectors.count(row[base + 12]),
                WeightVectors.count(row[base + 13]),
                WeightVectors.count(row[base + 14])
            )
        );
    }

    private static DocStats doc(final String[] row, final int from) {
        return new DocStats(
            WeightVectors.count(row[from]),
            WeightVectors.count(row[from + 1]),
            WeightVectors.count(row[from + 2])
        );
    }

    private static double number(final String text) {
        final double result;
        if (text.endsWith("nan")) {
            result = Double.NaN;
        } else if ("inf".equals(text)) {
            result = Double.POSITIVE_INFINITY;
        } else if ("-inf".equals(text)) {
            result = Double.NEGATIVE_INFINITY;
        } else {
            result = Double.parseDouble(text);
        }
        return result;
    }

    private static TermCount count(final String text) {
        return new TermCount(Long.parseLong(text));
    }
}
