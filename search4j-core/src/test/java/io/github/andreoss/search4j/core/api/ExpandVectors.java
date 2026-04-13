/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.inmemory.InMemoryShard;
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
 * Replays expansion sets captured from the reference library, each expanded
 * from the best hundred matches of a search under the default, the Bo1 or
 * the probabilistic scheme with an explicit k: terms, order and weights.
 *
 * @since 0.5.4
 */
final class ExpandVectors {

    /**
     * Separator of the expansion settings.
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
     * Expansions replayed against the corrected sets.
     */
    private final Set<String> corrected;

    /**
     * Number of expansions replayed.
     */
    private int expansions;

    ExpandVectors() {
        this.queries = new HashMap<>(128);
        this.shards = new HashMap<>(4);
        this.failures = new ArrayList<>(0);
        this.corrected = new TreeSet<>();
    }

    List<String> check() throws IOException {
        for (final String[] row : WeightVectors.rows("matcher-queries.tsv")) {
            this.queries.put(row[0], row);
        }
        final Map<String, List<String[]>> fixed = new HashMap<>(16);
        final Map<String, String[]> heads = ExpandVectors.sizes(
            "expand-corrected-vectors.tsv", fixed
        );
        final Map<String, List<String[]>> items = new HashMap<>(4096);
        for (final Map.Entry<String, String[]> entry
            : ExpandVectors.sizes("expand-vectors.tsv", items).entrySet()) {
            this.expansions = this.expansions + 1;
            if (!heads.containsKey(entry.getKey())) {
                this.compare(
                    entry.getValue(), items.getOrDefault(entry.getKey(), List.of())
                );
            }
        }
        for (final Map.Entry<String, String[]> entry : heads.entrySet()) {
            this.corrected.add(entry.getKey());
            this.compare(
                entry.getValue(), fixed.getOrDefault(entry.getKey(), List.of())
            );
        }
        return this.failures;
    }

    int expansions() {
        return this.expansions;
    }

    Set<String> corrected() {
        return this.corrected;
    }

    private static Map<String, String[]> sizes(
        final String name, final Map<String, List<String[]>> items
    ) throws IOException {
        final Map<String, String[]> result = new LinkedHashMap<>(4096);
        for (final String[] row : WeightVectors.rows(name)) {
            final String key = String.join("\t", row[1], row[2], row[3]);
            if ("E".equals(row[0])) {
                items.computeIfAbsent(key, ignored -> new ArrayList<>(8)).add(row);
            } else {
                result.put(key, row);
            }
        }
        return result;
    }

    private void compare(final String[] head, final List<String[]> expected) {
        final List<ESet.Item> found = this.expand(head[1], head[2], head[3]);
        if (found.size() != Integer.parseInt(head[4])) {
            this.failures.add(
                String.format(
                    "%s expected %s got %s", String.join(" ", head), head[4],
                    ExpandVectors.text(found)
                )
            );
        }
        for (final String[] row : expected) {
            final int rank = Integer.parseInt(row[4]);
            if (rank >= found.size() || !found.get(rank).term().equals(row[5])) {
                this.failures.add(
                    String.format(
                        "%s got %s", String.join(" ", row), ExpandVectors.text(found)
                    )
                );
            } else {
                this.close(row[6], found.get(rank).weight(), row);
            }
        }
    }

    private List<ESet.Item> expand(
        final String spec, final String name, final String setting
    ) {
        final String[] parts = ExpandVectors.COMMA.split(setting, -1);
        final Enquire enquire = new EnquireImpl(
            this.shards.computeIfAbsent(
                this.queries.get(name)[1], WeightFixture::valued
            )
        );
        enquire.setQuery(MatcherVectors.parse(this.queries.get(name)[2]));
        if (!"-".equals(spec)) {
            enquire.setWeightingScheme(Weight.create(spec, new Registry()));
        }
        enquire.setCutoff(Integer.parseInt(parts[2]), Double.parseDouble(parts[3]));
        final int max = Integer.parseInt(parts[0]);
        final ESet found;
        if ("-".equals(parts[1])) {
            found = enquire.getESet(max);
        } else if ("bo1".equals(parts[1])) {
            found = enquire.getESet(max, new Bo1EWeight());
        } else {
            found = enquire.getESet(
                max, new ProbeWeight(Double.parseDouble(parts[1].substring(5)))
            );
        }
        return found.toList();
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

    private static String text(final List<ESet.Item> items) {
        final StringBuilder out = new StringBuilder(64);
        for (final ESet.Item item : items) {
            out.append(item.term()).append(':').append(item.weight()).append(' ');
        }
        return out.toString();
    }
}
