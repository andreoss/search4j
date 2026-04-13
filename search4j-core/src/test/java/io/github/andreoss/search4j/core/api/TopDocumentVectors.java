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
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Replays the two best documents captured from the reference library for
 * each search: the same documents, in the same order, with the same
 * weights.
 *
 * <p>Searches whose captured best documents are changed by a known pruning
 * defect of the reference are deferred, each with its cause, and replayed
 * against those captured from a build with the defect corrected.</p>
 *
 * @since 0.5.2
 */
final class TopDocumentVectors {

    /**
     * Number of best documents captured.
     */
    private static final int TOP = 2;

    /**
     * Expressions by query identifier.
     */
    private final Map<String, String> queries;

    /**
     * Corpora by query identifier.
     */
    private final Map<String, String> corpora;

    /**
     * Shards by corpus.
     */
    private final Map<String, InMemoryShard> shards;

    /**
     * Failures found.
     */
    private final List<String> failures;

    /**
     * Causes of the deferred searches, by search.
     */
    private final Map<String, String> deferred;

    /**
     * Deferred searches found among the captured ones.
     */
    private final Set<String> skipped;

    /**
     * Searches replayed.
     */
    private final Set<String> replayed;

    TopDocumentVectors() {
        this.queries = new HashMap<>(128);
        this.corpora = new HashMap<>(128);
        this.shards = new HashMap<>(4);
        this.failures = new ArrayList<>(0);
        this.deferred = new TreeMap<>();
        this.skipped = new TreeSet<>();
        this.replayed = new TreeSet<>();
    }

    List<String> check() throws IOException {
        this.load();
        final Map<String, List<String[]>> groups =
            TopDocumentVectors.groups("matcher-vectors.tsv");
        final Set<String> specs = new TreeSet<>();
        for (final String key : groups.keySet()) {
            specs.add(key.substring(0, key.indexOf('\t')));
        }
        for (final String spec : specs) {
            for (final String name : this.queries.keySet()) {
                final String key = String.join("\t", spec, name);
                if (this.deferred.containsKey(key)) {
                    this.skipped.add(key);
                } else {
                    this.replayed.add(key);
                    this.compare(spec, name, groups.getOrDefault(key, List.of()));
                }
            }
        }
        return this.failures;
    }

    List<String> checkCorrected() throws IOException {
        this.load();
        for (final Map.Entry<String, List<String[]>> entry
            : TopDocumentVectors.groups("matcher-corrected-vectors.tsv").entrySet()) {
            this.replayed.add(entry.getKey());
            this.compare(
                entry.getValue().get(0)[1], entry.getValue().get(0)[2],
                entry.getValue()
            );
        }
        return this.failures;
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

    Set<String> replayed() {
        return this.replayed;
    }

    private void load() throws IOException {
        for (final String[] row : WeightVectors.rows("matcher-queries.tsv")) {
            this.corpora.put(row[0], row[1]);
            this.queries.put(row[0], row[2]);
        }
        for (final String[] row : WeightVectors.rows("matcher-deferred.tsv")) {
            this.deferred.put(String.join("\t", row[1], row[2]), row[0]);
        }
    }

    private static Map<String, List<String[]>> groups(final String name)
        throws IOException {
        final Map<String, List<String[]>> result = new LinkedHashMap<>(256);
        for (final String[] row : WeightVectors.rows(name)) {
            if ("K".equals(row[0])) {
                result.computeIfAbsent(
                    String.join("\t", row[1], row[2]),
                    key -> new ArrayList<>(TopDocumentVectors.TOP)
                ).add(row);
            }
        }
        return result;
    }

    private void compare(
        final String spec, final String name, final List<String[]> expected
    ) {
        final Enquire enquire = new EnquireImpl(
            this.shards.computeIfAbsent(
                this.corpora.get(name), WeightFixture::valued
            )
        );
        enquire.setQuery(
            MatcherVectors.parse(this.queries.get(name)),
            Weight.create(spec, new Registry())
        );
        final List<MSet.Item> found = enquire.getMSet(0, TopDocumentVectors.TOP)
            .toList();
        final StringBuilder want = new StringBuilder(64);
        for (final String[] row : expected) {
            want.append(row[4]).append(' ');
        }
        final StringBuilder got = new StringBuilder(64);
        for (final MSet.Item item : found) {
            got.append(item.docId().value()).append(' ');
        }
        if (want.toString().equals(got.toString())) {
            for (int rank = 0; rank < found.size(); rank = rank + 1) {
                this.close(expected.get(rank), found.get(rank).weight());
            }
        } else {
            this.failures.add(
                String.format(
                    "%s %s [%s] expected %sgot %s", spec, name,
                    this.queries.get(name), want, got
                )
            );
        }
    }

    private void close(final String[] row, final double actual) {
        final double expected = Double.parseDouble(row[5]);
        if (Math.abs(expected - actual)
            > 1e-9 * Math.max(Math.abs(expected), Math.abs(actual))
            || Double.isNaN(actual)) {
            this.failures.add(
                String.format(
                    "%s expected %s got %s", String.join(" ", row), row[5], actual
                )
            );
        }
    }
}
