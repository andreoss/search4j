/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.api.query.EditDistance;
import io.github.andreoss.search4j.core.api.query.Expansion;
import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.api.query.Wildcard;
import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.backend.inmemory.InMemoryShard;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.ValueNo;
import io.github.andreoss.search4j.core.types.WildcardLimit;
import io.github.andreoss.search4j.core.types.WildcardPattern;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Queries and corpus with positions of the captured positional vectors.
 *
 * @since 0.10.0
 */
final class PositionalQueries {

    /**
     * Separator of document tokens.
     */
    private static final Pattern SPACE = Pattern.compile(" ");

    /**
     * Splits an expression into tokens.
     */
    private static final Pattern TOKENS = Pattern.compile("(?=[()])|(?<=[()])| +");

    /**
     * Patterns by letter.
     */
    private static final Map<String, WildcardPattern> GLOBS = Map.of(
        "P", WildcardPattern.PREFIX, "M", WildcardPattern.MULTI,
        "S", WildcardPattern.SINGLE, "G", WildcardPattern.GLOB
    );

    /**
     * Limits by letter.
     */
    private static final Map<String, WildcardLimit> LIMITS = Map.of(
        "E", WildcardLimit.ERROR, "F", WildcardLimit.FIRST,
        "M", WildcardLimit.MOST_FREQUENT
    );

    /**
     * Utility class, no instances.
     */
    private PositionalQueries() {
    }

    static Query parse(final String expression) {
        final List<String> tokens = new ArrayList<>(32);
        for (final String token : PositionalQueries.TOKENS.split(expression.trim(), -1)) {
            if (!token.isBlank()) {
                tokens.add(token.strip());
            }
        }
        return PositionalQueries.parse(tokens, new int[1]);
    }

    static InMemoryShard shard(final String corpus) {
        final Map<Integer, DocumentInternal> docs = new TreeMap<>();
        try {
            for (final String[] row : WeightVectors.rows("positional-corpus.tsv")) {
                if (row[0].equals(corpus)) {
                    docs.put(Integer.parseInt(row[1]), PositionalQueries.document(row));
                }
            }
        } catch (final IOException ex) {
            throw new IllegalStateException(ex);
        }
        final InMemoryShard shard = new InMemoryShard();
        for (final Map.Entry<Integer, DocumentInternal> doc : docs.entrySet()) {
            shard.replaceDocument(DocId.of(doc.getKey()), doc.getValue());
        }
        return shard;
    }

    private static Query parse(final List<String> tokens, final int... cursor) {
        final String token = PositionalQueries.take(tokens, cursor);
        final Query result;
        if ("(".equals(token)) {
            result = PositionalQueries.branch(tokens, cursor);
            PositionalQueries.take(tokens, cursor);
        } else {
            result = MatcherVectors.parse(token);
        }
        return result;
    }

    private static Query branch(final List<String> tokens, final int... cursor) {
        final String operator = PositionalQueries.take(tokens, cursor);
        final Query result;
        if ("SCALE".equals(operator)) {
            result = Query.scale(
                Double.parseDouble(PositionalQueries.take(tokens, cursor)),
                PositionalQueries.parse(tokens, cursor)
            );
        } else if ("WILD".equals(operator)) {
            result = PositionalQueries.wildcard(tokens, cursor);
        } else if ("EDIT".equals(operator)) {
            result = PositionalQueries.edit(tokens, cursor);
        } else if ("RANGE".equals(operator) || "GE".equals(operator)
            || "LE".equals(operator)) {
            result = PositionalQueries.value(operator, tokens, cursor);
        } else {
            result = PositionalQueries.listed(operator, tokens, cursor);
        }
        return result;
    }

    private static Query listed(
        final String operator, final List<String> tokens, final int... cursor
    ) {
        QueryOp type = QueryOp.ELITE_SET;
        int parameter = 0;
        if ("ELITE".equals(operator)) {
            parameter = Integer.parseUnsignedInt(PositionalQueries.take(tokens, cursor));
        } else {
            type = QueryOp.valueOf(operator);
        }
        if (type == QueryOp.PHRASE || type == QueryOp.NEAR) {
            parameter = Integer.parseUnsignedInt(PositionalQueries.take(tokens, cursor));
        }
        final List<Query> subs = new ArrayList<>(4);
        while (!")".equals(tokens.get(cursor[0]))) {
            subs.add(PositionalQueries.parse(tokens, cursor));
        }
        return Query.of(type, subs, parameter);
    }

    private static Query value(
        final String operator, final List<String> tokens, final int... cursor
    ) {
        final StringBuilder text = new StringBuilder(32).append('(').append(operator);
        while (!")".equals(tokens.get(cursor[0]))) {
            text.append(' ').append(PositionalQueries.take(tokens, cursor));
        }
        return MatcherVectors.parse(text.append(')').toString());
    }

    private static Query wildcard(final List<String> tokens, final int... cursor) {
        final List<String> args = tokens.subList(cursor[0], cursor[0] + 5);
        cursor[0] = cursor[0] + 5;
        return Query.wildcard(
            new Wildcard(
                args.get(0), PositionalQueries.expansion(args.get(1), args.get(2)),
                PositionalQueries.GLOBS.get(args.get(3)), QueryOp.valueOf(args.get(4))
            )
        );
    }

    private static Query edit(final List<String> tokens, final int... cursor) {
        final List<String> args = tokens.subList(cursor[0], cursor[0] + 6);
        cursor[0] = cursor[0] + 6;
        return Query.editDistance(
            new EditDistance(
                args.get(0), PositionalQueries.expansion(args.get(3), args.get(4)),
                QueryOp.valueOf(args.get(5)), Integer.parseInt(args.get(1)),
                Integer.parseInt(args.get(2))
            )
        );
    }

    private static Expansion expansion(final String max, final String limit) {
        return new Expansion(
            new TermCount(Long.parseLong(max)), PositionalQueries.LIMITS.get(limit)
        );
    }

    private static String take(final List<String> tokens, final int... cursor) {
        final String token = tokens.get(cursor[0]);
        cursor[0] = cursor[0] + 1;
        return token;
    }

    private static DocumentInternal document(final String... row) {
        final DocumentInternal doc = new DocumentInternal();
        long position = 0L;
        for (final String token : PositionalQueries.SPACE.split(row[2], -1)) {
            final int colon = token.indexOf(':');
            if ("_".equals(token)) {
                position = TermPos.wrap(position + 1L).value();
            } else if (token.startsWith("@")) {
                position = TermPos.wrap(Long.parseLong(token.substring(1)) - 1L).value();
            } else if (colon < 0) {
                position = TermPos.wrap(position + 1L).value();
                doc.addPosting(token, new TermPos(position), TermCount.ONE);
            } else {
                doc.addTerm(
                    token.substring(0, colon),
                    new TermCount(Long.parseLong(token.substring(colon + 1)))
                );
            }
        }
        if (row.length > 3) {
            final int colon = row[3].indexOf(':');
            doc.addValue(
                ValueNo.parse(row[3].substring(0, colon)), row[3].substring(colon + 1)
            );
        }
        return doc;
    }
}
