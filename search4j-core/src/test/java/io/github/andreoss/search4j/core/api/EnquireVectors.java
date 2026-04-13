/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.inmemory.InMemoryShard;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Replays match sets captured from the reference library for searches that
 * match more documents than requested: documents, order, weights, item
 * counts and the bounds on the number of matches.
 *
 * <p>Searches whose captured result is changed by a known pruning defect of
 * the reference are deferred, each with its cause, and replayed against the
 * match sets captured from a build with the defect corrected.</p>
 *
 * @since 0.4.0
 */
final class EnquireVectors {

    /**
     * Separator of the search settings.
     */
    private static final Pattern COMMA = Pattern.compile(",");

    /**
     * Expressions by query identifier.
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
     * Number of searches replayed.
     */
    private int searches;

    /**
     * Causes of the deferred searches, by search.
     */
    private final Map<String, String> deferred;

    /**
     * Deferred searches found among the captured ones.
     */
    private final Set<String> skipped;

    /**
     * Searches replayed against the corrected match sets.
     */
    private final Set<String> corrected;

    EnquireVectors() {
        this.queries = new HashMap<>(128);
        this.shards = new HashMap<>(4);
        this.failures = new ArrayList<>(0);
        this.deferred = new TreeMap<>();
        this.skipped = new TreeSet<>();
        this.corrected = new TreeSet<>();
    }

    List<String> check() throws IOException {
        this.load();
        final Map<String, List<String[]>> items = new HashMap<>(16_384);
        for (final Map.Entry<String, String[]> entry
            : EnquireVectors.bounds("enquire-vectors.tsv", items).entrySet()) {
            this.searches = this.searches + 1;
            if (this.deferred.containsKey(entry.getKey())) {
                this.skipped.add(entry.getKey());
            } else {
                this.compare(
                    entry.getValue(), items.getOrDefault(entry.getKey(), List.of())
                );
            }
        }
        return this.failures;
    }

    List<String> checkCorrected() throws IOException {
        this.load();
        final Map<String, List<String[]>> items = new HashMap<>(64);
        for (final Map.Entry<String, String[]> entry
            : EnquireVectors.bounds("enquire-corrected-vectors.tsv", items)
                .entrySet()) {
            this.corrected.add(entry.getKey());
            this.compare(
                entry.getValue(), items.getOrDefault(entry.getKey(), List.of())
            );
        }
        return this.failures;
    }

    int searches() {
        return this.searches;
    }

    Set<String> deferred(final String cause) {
        final Set<String> result = new TreeSet<>();
        for (final Map.Entry<String, String> entry : this.deferred.entrySet()) {
            if (entry.getValue().equals(cause)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    Set<String> deferred() {
        return this.deferred.keySet();
    }

    Set<String> skipped() {
        return this.skipped;
    }

    Set<String> corrected() {
        return this.corrected;
    }

    private void load() throws IOException {
        for (final String[] row : WeightVectors.rows("matcher-queries.tsv")) {
            this.queries.put(row[0], row);
        }
        for (final String[] row : WeightVectors.rows("enquire-deferred.tsv")) {
            this.deferred.put(String.join("\t", row[1], row[2], row[3]), row[0]);
        }
    }

    private static Map<String, String[]> bounds(
        final String name, final Map<String, List<String[]>> items
    ) throws IOException {
        final Map<String, String[]> result = new LinkedHashMap<>(16_384);
        for (final String[] row : WeightVectors.rows(name)) {
            final String key = String.join("\t", row[1], row[2], row[3]);
            if ("M".equals(row[0])) {
                items.computeIfAbsent(key, ignored -> new ArrayList<>(8)).add(row);
            } else {
                result.put(key, row);
            }
        }
        return result;
    }

    private void compare(final String[] head, final List<String[]> expected) {
        final MSet found = this.search(head[1], head[2], head[3]);
        final List<MSet.Item> list = found.toList();
        this.same(
            String.join("\t", head[4], head[5], head[6], head[7]),
            String.join(
                "\t", Integer.toString(found.size().value()),
                Integer.toString(found.matchesLowerBound().value()),
                Integer.toString(found.matchesEstimated().value()),
                Integer.toString(found.matchesUpperBound().value())
            ),
            head
        );
        for (final String[] row : expected) {
            final int rank = Integer.parseInt(row[4]);
            if (rank >= list.size()
                || list.get(rank).docId().value() != Integer.parseInt(row[5])) {
                this.failures.add(
                    String.format(
                        "%s [%s] got %s", String.join(" ", row),
                        this.queries.get(row[2])[2], EnquireVectors.text(list)
                    )
                );
            } else {
                this.close(row[6], list.get(rank).weight(), row);
            }
        }
    }

    private MSet search(final String spec, final String name, final String setting) {
        final String[] parts = EnquireVectors.COMMA.split(setting, -1);
        final Enquire enquire = new EnquireImpl(
            this.shards.computeIfAbsent(
                this.queries.get(name)[1], WeightFixture::valued
            )
        );
        enquire.setQuery(MatcherVectors.parse(this.queries.get(name)[2]));
        enquire.setWeightingScheme(Weight.create(spec, new Registry()));
        enquire.setCutoff(Integer.parseInt(parts[3]), Double.parseDouble(parts[4]));
        final String sort = parts[5];
        if (sort.charAt(0) == 'V') {
            enquire.setSortByValue(
                new ValueNo(sort.charAt(1) - '0'), sort.length() == 2
            );
        }
        return enquire.getMSet(
            Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
            Integer.parseInt(parts[2])
        );
    }

    private void close(final String text, final double actual, final String... row) {
        final double expected = Double.parseDouble(text);
        if (Math.abs(expected - actual)
            > 1e-9 * Math.max(Math.abs(expected), Math.abs(actual))
            || Double.isNaN(actual)) {
            this.failures.add(
                String.format(
                    "%s expected %s got %s", String.join(" ", row), text, actual
                )
            );
        }
    }

    private void same(final String expected, final String actual, final String... row) {
        if (!expected.equals(actual)) {
            this.failures.add(
                String.format(
                    "%s [%s] expected %s got %s", String.join(" ", row),
                    this.queries.get(row[2])[2], expected, actual
                )
            );
        }
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
