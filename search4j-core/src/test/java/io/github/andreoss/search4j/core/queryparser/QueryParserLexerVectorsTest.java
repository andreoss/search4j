/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Captured query parser comparison vectors.
 *
 * @since 0.1.0
 */
package io.github.andreoss.search4j.core.queryparser;

import io.github.andreoss.search4j.core.error.QueryParserError;
import io.github.andreoss.search4j.core.stem.Stem;
import io.github.andreoss.search4j.core.termgen.Stopper;
import io.github.andreoss.search4j.core.types.ParseFlag;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.StemStrategy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks parser descriptions of brackets, quotes, signs, synonyms and word
 * shapes against output captured from the reference
 * command-line parser: query, flags, stemmer, strategy, default operator
 * and the printed description or error.
 *
 * @since 0.1.0
 */
final class QueryParserLexerVectorsTest {

    /**
     * Captured vector field delimiter.
     */
    private static final Pattern SEPARATOR = Pattern.compile("\\t");

    /**
     * Flag list delimiter.
     */
    private static final Pattern COMMA = Pattern.compile(",");

    /**
     * Flag names of the reference parser that differ from this project's.
     */
    private static final Map<String, String> ALIASES = Map.of(
        "spelling_correction", "spelling",
        "auto_multiword_synonyms", "group_synonyms",
        "no_proper_noun_heuristic", "stem_capitals"
    );

    /**
     * Stop words configured in the reference command-line parser.
     */
    private static final Stopper STOPPER = Stopper.of(
        "a", "about", "an", "and", "are", "as", "at", "be", "by", "en",
        "for", "from", "how", "i", "in", "is", "it", "of", "on", "or",
        "that", "the", "this", "to", "was", "what", "when", "where",
        "which", "who", "why", "will", "with"
    );

    @Test
    void comparesNativeVectors() throws IOException {
        final List<String> failures = new ArrayList<>(0);
        int count = 0;
        try (
            BufferedReader input = new BufferedReader(
                new InputStreamReader(
                    this.getClass().getResourceAsStream(
                        "reference-lexer-vectors.tsv"
                    ),
                    StandardCharsets.UTF_8
                )
            )
        ) {
            String line = input.readLine();
            while (line != null) {
                final String[] vector =
                    QueryParserLexerVectorsTest.SEPARATOR.split(line, 6);
                final String actual = QueryParserLexerVectorsTest
                    .describe(vector);
                if (!vector[5].equals(actual)) {
                    failures.add(
                        String.format(
                            "%s [%s %s %s %s] expected %s got %s",
                            vector[0], vector[1], vector[2], vector[3],
                            vector[4], vector[5], actual
                        )
                    );
                }
                count = count + 1;
                line = input.readLine();
            }
        }
        if (count == 0) {
            failures.add("no vectors read");
        }
        Assertions.assertEquals(
            List.of(), failures, "descriptions differing from the reference"
        );
    }

    private static String describe(final String... vector) {
        final QueryParser parser = new QueryParser();
        parser.stopper(QueryParserLexerVectorsTest.STOPPER);
        parser.stemmer(
            Stem.of(vector[2]),
            StemStrategy.valueOf(vector[3].toUpperCase(Locale.ROOT))
        );
        parser.defaultOp(QueryOp.valueOf(vector[4].toUpperCase(Locale.ROOT)));
        String text;
        try {
            text = parser.parse(
                vector[0], QueryParserLexerVectorsTest.flags(vector[1])
            ).description();
        } catch (final QueryParserError err) {
            text = String.format("Couldn't parse query: %s", err.getMessage());
        }
        return text;
    }

    private static Set<ParseFlag> flags(final String names) {
        final Set<ParseFlag> set = EnumSet.noneOf(ParseFlag.class);
        QueryParserLexerVectorsTest.COMMA.splitAsStream(names).forEach(
            name -> {
                if ("default".equals(name)) {
                    set.addAll(ParseFlag.DEFAULTS);
                } else {
                    set.add(
                        ParseFlag.valueOf(
                            QueryParserLexerVectorsTest.ALIASES
                                .getOrDefault(name, name)
                                .toUpperCase(Locale.ROOT)
                        )
                    );
                }
            }
        );
        return set;
    }
}
