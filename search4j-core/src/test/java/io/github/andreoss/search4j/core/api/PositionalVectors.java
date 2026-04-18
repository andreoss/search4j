/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.backend.inmemory.InMemoryShard;
import io.github.andreoss.search4j.core.error.SearchError;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Replays match sets captured from the reference library for positional,
 * synonym, maximum, elite set, wildcard and edit distance queries over a
 * corpus with term positions: query descriptions and construction errors,
 * documents, order, weights, item counts, bounds and search errors.
 *
 * <p>Searches whose captured result is changed by the accepted pruning
 * correction are replayed against the match sets captured from a build with
 * the correction applied.</p>
 *
 * @since 0.10.0
 */
final class PositionalVectors {

    /**
     * Separator of the search settings.
     */
    private static final Pattern COMMA = Pattern.compile(",");

    /**
     * Rows of each query, by identifier.
     */
    private final Map<String, String[]> queries;

    /**
     * Shards by corpus.
     */
    private final Map<String, InMemoryShard> shards;

    /**
     * Failures found.
     */
    private final List<String> failures;

    /**
     * Searches replayed.
     */
    private final Set<String> replayed;

    /**
     * Searches skipped in favour of the corrected vectors.
     */
    private final Set<String> skipped;

    /**
     * Captured vectors.
     */
    private final String captured;

    /**
     * Captured vectors of the corrected build, empty when none differ.
     */
    private final String corrected;

    PositionalVectors() {
        this("positional-vectors.tsv", "positional-corrected-vectors.tsv");
    }

    PositionalVectors(final String vectors, final String fixes) {
        this.captured = vectors;
        this.corrected = fixes;
        this.queries = new HashMap<>(256);
        this.shards = new HashMap<>(4);
        this.failures = new ArrayList<>(0);
        this.replayed = new TreeSet<>();
        this.skipped = new TreeSet<>();
    }

    List<String> descriptions() throws IOException {
        for (final String[] row : WeightVectors.rows(this.captured)) {
            if ("Q".equals(row[0])) {
                String actual;
                try {
                    actual = this.query(row[1]).toString();
                } catch (final SearchError ex) {
                    actual = String.format("ERROR %s %s", ex.getType(), ex.getMessage());
                }
                this.same(row[2], actual, row);
            }
        }
        return this.failures;
    }

    List<String> check() throws IOException {
        final Set<String> fixed = PositionalVectors.searches(this.corrected).keySet();
        for (final Map.Entry<String, List<String[]>> entry
            : PositionalVectors.searches(this.captured).entrySet()) {
            if (fixed.contains(entry.getKey())) {
                this.skipped.add(entry.getKey());
            } else {
                this.replayed.add(entry.getKey());
                this.compare(entry.getValue());
            }
        }
        return this.failures;
    }

    List<String> checkCorrected() throws IOException {
        for (final List<String[]> rows
            : PositionalVectors.searches(this.corrected).values()) {
            this.compare(rows);
        }
        return this.failures;
    }

    Set<String> replayed() {
        return this.replayed;
    }

    Set<String> skipped() {
        return this.skipped;
    }

    private Map<String, String[]> queries() {
        if (this.queries.isEmpty()) {
            try {
                for (final String[] row : WeightVectors.rows("positional-queries.tsv")) {
                    this.queries.put(row[0], row);
                }
            } catch (final IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return this.queries;
    }

    private static Map<String, List<String[]>> searches(final String name)
        throws IOException {
        final Map<String, List<String[]>> result = new LinkedHashMap<>(16_384);
        final List<String[]> rows = new ArrayList<>(16_384);
        if (!name.isEmpty()) {
            rows.addAll(WeightVectors.rows(name));
        }
        for (final String[] row : rows) {
            if (!"Q".equals(row[0])) {
                result.computeIfAbsent(
                    String.join("\t", row[1], row[2], row[3]),
                    ignored -> new ArrayList<>(8)
                ).add(row);
            }
        }
        return result;
    }

    private void compare(final List<String[]> rows) {
        final String[] head = rows.get(0);
        String error = "";
        List<MSet.Item> items = List.of();
        MSet found = null;
        try {
            found = this.search(head[1], head[2], head[3]);
            items = found.toList();
        } catch (final SearchError ex) {
            error = String.join("\t", ex.getType(), ex.getMessage());
        }
        int count = 0;
        for (final String[] row : rows) {
            if ("E".equals(row[0])) {
                this.same(String.join("\t", row[4], row[5]), error, row);
            } else if (found == null) {
                this.same("no error", error, row);
            } else if ("B".equals(row[0])) {
                this.same(
                    String.join("\t", row[4], row[5], row[6], row[7]),
                    String.join(
                        "\t", Long.toString(found.size().value()),
                        Long.toString(found.matchesLowerBound().value()),
                        Long.toString(found.matchesEstimated().value()),
                        Long.toString(found.matchesUpperBound().value())
                    ),
                    row
                );
            } else {
                count = count + 1;
                this.item(row, items);
            }
        }
        if (found != null) {
            this.same(Integer.toString(count), Integer.toString(items.size()), head);
        }
    }

    private void item(final String[] row, final List<MSet.Item> items) {
        final int rank = Integer.parseInt(row[4]);
        if (rank >= items.size()
            || items.get(rank).docId().value() != Integer.parseInt(row[5])) {
            this.failures.add(
                String.format(
                    "%s [%s] got %s", String.join(" ", row),
                    this.queries().get(row[2])[2], PositionalVectors.text(items)
                )
            );
        } else {
            final double expected = PositionalVectors.number(row[6]);
            final double actual = items.get(rank).weight();
            if (Double.isInfinite(expected) && expected != actual
                || Math.abs(expected - actual)
                > 1e-9 * Math.max(Math.abs(expected), Math.abs(actual))
                || Double.isNaN(actual)) {
                this.failures.add(
                    String.format(
                        "%s [%s] expected %s got %s", String.join(" ", row),
                        this.queries().get(row[2])[2], row[6], actual
                    )
                );
            }
        }
    }

    private MSet search(final String spec, final String name, final String setting) {
        final String[] parts = PositionalVectors.COMMA.split(setting, -1);
        final Enquire enquire = new EnquireImpl(
            this.shards.computeIfAbsent(
                this.queries().get(name)[1], PositionalQueries::shard
            )
        );
        enquire.setQuery(this.query(name));
        enquire.setWeightingScheme(Weight.create(spec, new Registry()));
        enquire.setCutoff(Integer.parseInt(parts[3]), Double.parseDouble(parts[4]));
        return enquire.getMSet(
            Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
            Integer.parseInt(parts[2])
        );
    }

    private Query query(final String name) {
        return PositionalQueries.parse(this.queries().get(name)[2]);
    }

    private void same(final String expected, final String actual, final String... row) {
        if (!expected.equals(actual)) {
            this.failures.add(
                String.format(
                    "%s [%s] expected %s got %s", String.join(" ", row),
                    this.queries().get(PositionalVectors.name(row))[2],
                    expected, actual
                )
            );
        }
    }

    private static double number(final String text) {
        final double result;
        if ("inf".equals(text)) {
            result = Double.POSITIVE_INFINITY;
        } else {
            result = Double.parseDouble(text);
        }
        return result;
    }

    private static String name(final String... row) {
        String result = row[2];
        if ("Q".equals(row[0])) {
            result = row[1];
        }
        return result;
    }

    private static String text(final List<MSet.Item> items) {
        final StringBuilder out = new StringBuilder(64);
        for (final MSet.Item item : items) {
            out.append(item.docId().value()).append(':')
                .append(item.weight()).append(' ');
        }
        return out.toString();
    }
}
