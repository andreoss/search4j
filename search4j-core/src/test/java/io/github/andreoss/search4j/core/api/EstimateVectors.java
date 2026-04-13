/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.backend.inmemory.InMemoryShard;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Replays match sets captured from the reference library over a generated
 * corpus of four thousand documents, where early termination leaves wide
 * bounds on the number of matches: documents, weights, collapse counts and
 * keys, the bounds, and the estimates after and before collapsing as the
 * reference reports them, next to the unrounded estimates it holds.
 *
 * @since 0.7.1
 */
final class EstimateVectors {

    /**
     * Separator of the search settings.
     */
    private static final Pattern COMMA = Pattern.compile(",");

    /**
     * Separator of the terms of a document.
     */
    private static final Pattern SPACE = Pattern.compile(" ");

    /**
     * Expressions by query identifier.
     */
    private final Map<String, String> queries;

    /**
     * Shard of the generated corpus.
     */
    private final InMemoryShard shard;

    /**
     * Failures found.
     */
    private final List<String> failures;

    /**
     * Captured searches, by kind of count.
     */
    private final Map<String, Integer> tallies;

    EstimateVectors() {
        this.queries = new HashMap<>(32);
        this.shard = new InMemoryShard();
        this.failures = new ArrayList<>(0);
        this.tallies = new HashMap<>(4);
    }

    List<String> check() throws IOException {
        this.load();
        final Map<String, List<String[]>> items = new HashMap<>(1024);
        final List<String[]> heads = new ArrayList<>(1024);
        for (final String[] row : WeightVectors.rows("estimate-vectors.tsv")) {
            if ("I".equals(row[0])) {
                items.computeIfAbsent(
                    String.join("\t", row[1], row[2]), ignored -> new ArrayList<>(10)
                ).add(row);
            } else {
                heads.add(row);
            }
        }
        for (final String[] head : heads) {
            this.compare(
                head, items.getOrDefault(String.join("\t", head[1], head[2]), List.of())
            );
        }
        return this.failures;
    }

    List<String> rounding() throws IOException {
        for (final String[] row : WeightVectors.rows("estimate-vectors.tsv")) {
            if ("H".equals(row[0])) {
                this.rounds(row, 4, 10);
                this.rounds(row, 7, 11);
            }
        }
        return this.failures;
    }

    int tally(final String kind) {
        return this.tallies.getOrDefault(kind, 0);
    }

    private void compare(final String[] head, final List<String[]> expected) {
        this.tallies.merge("searches", 1, Integer::sum);
        if (Integer.parseInt(head[6]) - Integer.parseInt(head[4]) > 10) {
            this.tallies.merge("wide", 1, Integer::sum);
        }
        if (!head[5].equals(head[10]) || !head[8].equals(head[11])) {
            this.tallies.merge("rounded", 1, Integer::sum);
        }
        final MSet found = this.search(head[1], head[2]);
        this.same(
            String.join(
                "\t", head[3], head[4], head[5], head[6], head[7], head[8], head[9]
            ),
            EstimateVectors.reported(found), head
        );
        final List<MSet.Item> list = found.toList();
        this.same(Integer.toString(expected.size()), Integer.toString(list.size()), head);
        for (final String[] row : expected) {
            final int rank = Integer.parseInt(row[3]);
            if (rank < list.size()) {
                final MSet.Item item = list.get(rank);
                this.same(
                    String.join("\t", row[4], row[6], row[7]),
                    String.join(
                        "\t", Long.toString(item.docId().value()),
                        Long.toString(item.collapseCount().value()), item.collapseKey()
                    ),
                    row
                );
                this.close(row[5], item.weight(), row);
            }
        }
    }

    private void rounds(final String[] head, final int lower, final int raw) {
        this.same(
            head[lower + 1],
            Long.toString(
                new RoundedEstimate(
                    EstimateVectors.count(head[lower]), EstimateVectors.count(head[raw]),
                    EstimateVectors.count(head[lower + 2])
                ).value().value()
            ),
            head
        );
    }

    private MSet search(final String name, final String setting) {
        final String[] parts = EstimateVectors.COMMA.split(setting, -1);
        final Enquire enquire = new EnquireImpl(this.shard);
        enquire.setQuery(MatcherVectors.parse(this.queries.get(name)));
        if (!"-".equals(parts[3])) {
            final ValueNo slot = new ValueNo(Integer.parseInt(parts[3]));
            final InMemoryShard docs = this.shard;
            enquire.setKeyMaker(
                docid -> docs.openDocument(docid, false)
                    .flatMap(document -> document.value(slot)).orElse("")
            );
            enquire.setCollapseMax(Integer.parseInt(parts[4]));
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
                    "%s expected %s got %s", String.join(" ", row), expected, actual
                )
            );
        }
    }

    private static String reported(final MSet found) {
        return String.join(
            "\t", Long.toString(found.size().value()),
            Long.toString(found.matchesLowerBound().value()),
            Long.toString(found.matchesEstimated().value()),
            Long.toString(found.matchesUpperBound().value()),
            Long.toString(found.uncollapsedMatchesLowerBound().value()),
            Long.toString(found.uncollapsedMatchesEstimated().value()),
            Long.toString(found.uncollapsedMatchesUpperBound().value())
        );
    }

    private static TermCount count(final String text) {
        return new TermCount(Integer.parseInt(text));
    }

    private void load() throws IOException {
        for (final String[] row : WeightVectors.rows("estimate-queries.tsv")) {
            this.queries.put(row[0], row[1]);
        }
        for (final String[] row : WeightVectors.rows("estimate-corpus.tsv")) {
            final DocumentInternal doc = new DocumentInternal();
            for (final String term : EstimateVectors.SPACE.split(row[1], -1)) {
                final int colon = term.indexOf(':');
                doc.addTerm(
                    term.substring(0, colon),
                    EstimateVectors.count(term.substring(colon + 1))
                );
            }
            for (int slot = 0; slot < 2; slot = slot + 1) {
                if (!row[slot + 2].isEmpty()) {
                    doc.addValue(new ValueNo(slot), row[slot + 2]);
                }
            }
            this.shard.replaceDocument(DocId.of(Integer.parseInt(row[0])), doc);
        }
    }
}
