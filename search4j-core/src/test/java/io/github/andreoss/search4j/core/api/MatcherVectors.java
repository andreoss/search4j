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
import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.matcher.MatchEstimates;
import io.github.andreoss.search4j.core.matcher.Matcher;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Replays match vectors captured from the reference library over the shared
 * corpus with values: documents, order, weights, counts and estimates.
 *
 * @since 0.3.0
 */
final class MatcherVectors {

    /**
     * Relative tolerance of weights.
     */
    private static final double TOLERANCE = 1e-9;

    /**
     * Expression token boundaries.
     */
    private static final Pattern TOKENS = Pattern.compile(
        "(?=[()])|(?<=[()])| +"
    );

    /**
     * Queries by identifier: corpus and expression.
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

    MatcherVectors() {
        this.queries = new HashMap<>(128);
        this.shards = new HashMap<>(4);
        this.failures = new ArrayList<>(0);
    }

    List<String> check(final String kinds) throws IOException {
        for (final String[] row : WeightVectors.rows("matcher-queries.tsv")) {
            this.queries.put(row[0], row);
        }
        final Map<String, List<String[]>> groups = new HashMap<>(256);
        for (final String[] row : WeightVectors.rows("matcher-vectors.tsv")) {
            if (kinds.contains(row[0])) {
                if ("E".equals(row[0])) {
                    this.estimated(row);
                } else if ("C".equals(row[0])) {
                    this.counted(row);
                } else {
                    groups.computeIfAbsent(
                        String.join("\t", row[0], row[1], row[2]),
                        key -> new ArrayList<>(8)
                    ).add(row);
                }
            }
        }
        for (final List<String[]> group : groups.values()) {
            if ("D".equals(group.get(0)[0])) {
                this.ranked(group);
            } else {
                this.pruned(group);
            }
        }
        return this.failures;
    }

    static Query parse(final String expression) {
        final List<String> tokens = new ArrayList<>(32);
        for (final String token : MatcherVectors.TOKENS.split(expression.trim(), -1)) {
            if (!token.isBlank()) {
                tokens.add(token.strip());
            }
        }
        return MatcherVectors.parse(tokens, new int[1]);
    }

    private static Query parse(final List<String> tokens, final int... cursor) {
        final String token = MatcherVectors.take(tokens, cursor);
        final Query result;
        if ("(".equals(token)) {
            result = MatcherVectors.branch(tokens, cursor);
            MatcherVectors.take(tokens, cursor);
        } else {
            result = MatcherVectors.leaf(token);
        }
        return result;
    }

    private static Query branch(final List<String> tokens, final int... cursor) {
        final String operator = MatcherVectors.take(tokens, cursor);
        final Query result;
        if ("SCALE".equals(operator)) {
            result = MatcherVectors.scaled(
                Double.parseDouble(MatcherVectors.take(tokens, cursor)), tokens, cursor
            );
        } else if ("RANGE".equals(operator)) {
            result = Query.valueRange(
                ValueNo.parse(MatcherVectors.take(tokens, cursor)),
                MatcherVectors.take(tokens, cursor), MatcherVectors.take(tokens, cursor)
            );
        } else if ("GE".equals(operator) || "LE".equals(operator)) {
            result = Query.value(
                QueryOp.valueOf(String.format("VALUE_%s", operator)),
                ValueNo.parse(MatcherVectors.take(tokens, cursor)),
                MatcherVectors.take(tokens, cursor)
            );
        } else {
            final List<Query> subs = new ArrayList<>(4);
            while (!")".equals(tokens.get(cursor[0]))) {
                subs.add(MatcherVectors.parse(tokens, cursor));
            }
            result = Query.of(QueryOp.valueOf(operator), subs);
        }
        return result;
    }

    private static Query scaled(
        final double factor, final List<String> tokens, final int... cursor
    ) {
        return Query.scale(
            QueryOp.SCALE_WEIGHT, MatcherVectors.parse(tokens, cursor), factor
        );
    }

    private static Query leaf(final String token) {
        final Query result;
        final int colon = token.indexOf(':');
        if ("*".equals(token)) {
            result = Query.MATCH_ALL;
        } else if ("-".equals(token)) {
            result = Query.MATCH_NOTHING;
        } else if (colon < 0) {
            result = Query.term(token);
        } else {
            result = Query.term(
                token.substring(0, colon),
                new TermCount(Integer.parseInt(token.substring(colon + 1)))
            );
        }
        return result;
    }

    private static String take(final List<String> tokens, final int... cursor) {
        final String token = tokens.get(cursor[0]);
        cursor[0] = cursor[0] + 1;
        return token;
    }

    private void estimated(final String... row) {
        if ("0".equals(row[3])) {
            final InMemoryShard shard = this.shard(row[2]);
            final MatchEstimates found = Matcher.build(
                shard, this.query(row[2]), MatcherVectors.scheme(row[1])
            ).estimate().resolve(shard.docCount(), DocId.of(1), shard.lastDocId());
            this.same(
                String.join("\t", row[4], row[5], row[6]),
                String.join(
                    "\t", Integer.toString(found.min().value()),
                    Integer.toString(found.est().value()),
                    Integer.toString(found.max().value())
                ),
                row
            );
        }
    }

    private void counted(final String... row) {
        this.same(
            row[3], Integer.toString(this.search(row[1], row[2]).size()), row
        );
    }

    private void ranked(final List<String[]> expected) {
        final String[] head = expected.get(0);
        this.compare(expected, this.search(head[1], head[2]));
    }

    private void pruned(final List<String[]> expected) {
        final String[] head = expected.get(0);
        final InMemoryShard shard = this.shard(head[2]);
        double least = 0.0;
        if (expected.size() > 1) {
            least = Double.parseDouble(expected.get(expected.size() - 1)[5])
                * (1.0 - MatcherVectors.TOLERANCE);
        }
        PostList list = Matcher.resolve(
            shard, this.query(head[2]), MatcherVectors.scheme(head[1])
        );
        final List<double[]> seen = new ArrayList<>(16);
        list = list.next(least);
        while (!list.atEnd()) {
            final DocId docid = list.docId();
            final double weight = list.weight(
                new DocStats(
                    shard.docLength(docid), shard.uniqueTerms(docid),
                    shard.wdfDocMax(docid)
                )
            );
            if (weight >= least) {
                seen.add(new double[] {docid.value(), weight});
            }
            list = list.next(least);
        }
        for (final String[] row : expected) {
            this.kept(row, seen);
        }
    }

    private void kept(final String[] row, final List<double[]> seen) {
        double weight = Double.NaN;
        for (final double[] item : seen) {
            if ((int) item[0] == Integer.parseInt(row[4])) {
                weight = item[1];
            }
        }
        if (Double.isNaN(weight)) {
            this.failures.add(
                String.format(
                    "%s [%s] not kept, got %s", String.join(" ", row),
                    this.queries.get(row[2])[2], MatcherVectors.text(seen)
                )
            );
        } else {
            this.close(row[5], weight, row);
        }
    }

    private void compare(final List<String[]> expected, final List<double[]> items) {
        for (final String[] row : expected) {
            final int rank = Integer.parseInt(row[3]);
            if (rank >= items.size()
                || (int) items.get(rank)[0] != Integer.parseInt(row[4])) {
                this.failures.add(
                    String.format(
                        "%s got %s", String.join(" ", row), MatcherVectors.text(items)
                    )
                );
            } else {
                this.close(row[5], items.get(rank)[1], row);
            }
        }
    }

    private List<double[]> search(final String spec, final String name) {
        final Enquire enquire = new EnquireImpl(this.shard(name));
        enquire.setQuery(this.query(name), MatcherVectors.scheme(spec));
        final List<double[]> items = new ArrayList<>(16);
        for (final MSet.Item item : enquire.getMSet(0, 1000).toList()) {
            items.add(new double[] {item.docId().value(), item.weight()});
        }
        return items;
    }

    private Query query(final String name) {
        return MatcherVectors.parse(this.queries.get(name)[2]);
    }

    private InMemoryShard shard(final String name) {
        return this.shards.computeIfAbsent(
            this.queries.get(name)[1], WeightFixture::valued
        );
    }

    private void close(final String text, final double actual, final String... row) {
        final double expected = Double.parseDouble(text);
        if (Math.abs(expected - actual)
            > MatcherVectors.TOLERANCE * Math.max(Math.abs(expected), Math.abs(actual))
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
                    "%s [%s] got %s", String.join(" ", row),
                    this.queries.get(row[2])[2], actual
                )
            );
        }
    }

    private static Weight scheme(final String spec) {
        return Weight.create(spec, new Registry());
    }

    private static String text(final List<double[]> items) {
        final StringBuilder out = new StringBuilder(64);
        for (final double[] item : items) {
            out.append((int) item[0]).append(':').append(item[1]).append(' ');
        }
        return out.toString();
    }
}
