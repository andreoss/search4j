/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.andreoss.search4j.core.compat.differential;

import io.github.andreoss.search4j.core.error.QueryParserError;
import io.github.andreoss.search4j.core.queryparser.QueryParser;
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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Compares parser output against the reference parser over a corpus of
 * queries covering plain terms, boolean operators, phrases, positional
 * windows, wildcards, value ranges, stemming, synonyms and parser flags,
 * and over every committed captured vector.
 *
 * @since 0.1.0
 */
final class QueryParserDifferentialTest {

    /**
     * Collapses whitespace runs in a query description.
     */
    private static final Pattern SPACES = Pattern.compile("\\s+");

    /**
     * Captured vector field delimiter.
     */
    private static final Pattern TAB = Pattern.compile("\\t");

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

    /**
     * Plain single-term queries.
     */
    private static final String[] PLAIN = {
        "hello",
        "world",
        "test",
        "document",
        "universe",
        "goodbye",
    };

    /**
     * Boolean operator queries.
     */
    private static final String[] BOOLEAN = {
        "hello AND world",
        "hello OR world",
        "hello AND NOT world",
        "hello XOR world",
        "(hello AND world) OR test",
        "hello AND (world OR universe)",
        "hello world",
        "+hello -world test",
        "a AND -b",
        "hello AND",
    };

    /**
     * Quoted phrase queries.
     */
    private static final String[] PHRASE = {
        "\"hello world\"",
        "\"hello universe\"",
        "\"goodbye world\"",
        "\"hello world test\"",
        "hello-world",
        "\"a-b c\"",
    };

    /**
     * Positional window queries, explicit and default.
     */
    private static final String[] WINDOWS = {
        "hello NEAR/3 world",
        "hello ADJ/3 world",
        "hello NEAR world",
        "hello ADJ world",
        "a NEAR/3 b",
        "a ADJ/1 b",
        "a NEAR/2 b NEAR/5 c",
        "x NEAR y z",
    };

    /**
     * Wildcard queries.
     */
    private static final String[] WILDCARDS = {
        "hello*",
        "hel*o",
        "*world",
        "te*t",
        "doc*",
    };

    /**
     * Value range queries.
     */
    private static final String[] RANGES = {
        "VALUE:0:title1..title3",
        "VALUE:1:category1..category2",
        "VALUE:0:title1..title1",
    };

    /**
     * Queries exercised with stemming enabled.
     */
    private static final String[] STEMMED = {
        "testing",
        "tested",
        "tests",
        "documents",
    };

    /**
     * Synonym operator queries.
     */
    private static final String[] SYNONYMS = {
        "hello SYNONYM world",
        "test SYNONYM document",
        "hello SYN world",
    };

    /**
     * Parser flag names exercised on one query.
     */
    private static final String[] FLAGS = {
        "default",
        "boolean",
        "lovehate",
        "partial",
        "synonym",
        "auto_multiword_synonyms",
        "auto_synonyms",
        "wildcard",
        "default,wildcard",
        "pure_not",
        "no_positions",
    };

    @Test
    void parsesPlainTerms() throws IOException {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        Assertions.assertEquals(
            List.of(),
            QueryParserDifferentialTest.differing(
                QueryParserDifferentialTest.PLAIN, "default:none"
            ),
            "plain terms"
        );
    }

    @Test
    void parsesBooleanOperators() throws IOException {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        Assertions.assertEquals(
            List.of(),
            QueryParserDifferentialTest.differing(
                QueryParserDifferentialTest.BOOLEAN, "default:none"
            ),
            "boolean operators"
        );
    }

    @Test
    void parsesPhrases() throws IOException {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        Assertions.assertEquals(
            List.of(),
            QueryParserDifferentialTest.differing(
                QueryParserDifferentialTest.PHRASE, "default:none"
            ),
            "phrases"
        );
    }

    @Test
    void parsesPositionalWindows() throws IOException {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        Assertions.assertEquals(
            List.of(),
            QueryParserDifferentialTest.differing(
                QueryParserDifferentialTest.WINDOWS, "default:none"
            ),
            "positional windows"
        );
    }

    @Test
    void parsesWildcards() throws IOException {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        Assertions.assertEquals(
            List.of(),
            QueryParserDifferentialTest.differing(
                QueryParserDifferentialTest.WILDCARDS,
                "default:none", "default,wildcard:none"
            ),
            "wildcards"
        );
    }

    @Test
    void parsesValueRanges() throws IOException {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        Assertions.assertEquals(
            List.of(),
            QueryParserDifferentialTest.differing(
                QueryParserDifferentialTest.RANGES, "default:none"
            ),
            "value ranges"
        );
    }

    @Test
    void stemsTerms() throws IOException {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        Assertions.assertEquals(
            List.of(),
            QueryParserDifferentialTest.differing(
                QueryParserDifferentialTest.STEMMED,
                "default:porter"
            ),
            "stemmed terms"
        );
    }

    @Test
    void expandsSynonyms() throws IOException {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        Assertions.assertEquals(
            List.of(),
            QueryParserDifferentialTest.differing(
                QueryParserDifferentialTest.SYNONYMS, "default:none"
            ),
            "synonyms"
        );
    }

    @Test
    void honoursParserFlags() throws IOException {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        final String[] setups =
            new String[QueryParserDifferentialTest.FLAGS.length];
        for (int idx = 0; idx < setups.length; ++idx) {
            setups[idx] = String.format(
                "%s:none", QueryParserDifferentialTest.FLAGS[idx]
            );
        }
        Assertions.assertEquals(
            List.of(),
            QueryParserDifferentialTest.differing(
                new String[] {"hello world"}, setups
            ),
            "parser flags"
        );
    }

    @Test
    void matchesEveryCapturedVector() throws IOException {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        final List<String> failures = new ArrayList<>(0);
        try (
            BufferedReader input = new BufferedReader(
                new InputStreamReader(
                    QueryParserDifferentialTest.class.getResourceAsStream(
                        "/io/github/andreoss/search4j/core/queryparser/reference-parser-vectors.tsv"
                    ),
                    StandardCharsets.UTF_8
                )
            )
        ) {
            String line = input.readLine();
            while (line != null) {
                final String[] vector =
                    QueryParserDifferentialTest.TAB.split(line, 6);
                final String expected = NativeTools.parse(
                    vector[0], vector[1], vector[2], vector[3], vector[4]
                );
                final String actual = QueryParserDifferentialTest.project(
                    vector[0], vector[1], vector[2], vector[3], vector[4]
                );
                if (!expected.equals(vector[5]) || !expected.equals(actual)) {
                    failures.add(
                        String.format(
                            "%s [%s] native %s captured %s project %s",
                            vector[0],
                            String.join(
                                " ", vector[1], vector[2], vector[3], vector[4]
                            ),
                            expected, vector[5], actual
                        )
                    );
                }
                line = input.readLine();
            }
        }
        Assertions.assertEquals(List.of(), failures, "vectors differing");
    }

    private static List<String> differing(final String[] queries,
        final String... setups) throws IOException {
        final List<String> failures = new ArrayList<>(0);
        for (final String setup : setups) {
            final String[] parts = setup.split(":", 2);
            for (final String que : queries) {
                final String expected = QueryParserDifferentialTest.normalise(
                    NativeTools.parse(que, parts[0], parts[1], "some", "or")
                );
                final String actual = QueryParserDifferentialTest.normalise(
                    QueryParserDifferentialTest.project(
                        que, parts[0], parts[1], "some", "or"
                    )
                );
                if (!expected.equals(actual)) {
                    failures.add(
                        String.format(
                            "%s [%s] native %s project %s",
                            que, setup, expected, actual
                        )
                    );
                }
            }
        }
        return failures;
    }

    private static String project(final String que, final String... options) {
        final QueryParser parser = new QueryParser();
        parser.stopper(QueryParserDifferentialTest.STOPPER);
        parser.stemmer(
            Stem.of(options[1]),
            StemStrategy.valueOf(options[2].toUpperCase(Locale.ROOT))
        );
        parser.defaultOp(QueryOp.valueOf(options[3].toUpperCase(Locale.ROOT)));
        String text;
        try {
            text = parser.parse(
                que, QueryParserDifferentialTest.flags(options[0])
            ).description();
        } catch (final QueryParserError err) {
            text = String.format("Couldn't parse query: %s", err.getMessage());
        }
        return text;
    }

    private static Set<ParseFlag> flags(final String names) {
        final Set<ParseFlag> set = EnumSet.noneOf(ParseFlag.class);
        QueryParserDifferentialTest.COMMA.splitAsStream(names).forEach(
            name -> {
                if ("default".equals(name)) {
                    set.addAll(ParseFlag.DEFAULTS);
                } else {
                    set.add(
                        ParseFlag.valueOf(
                            QueryParserDifferentialTest.ALIASES
                                .getOrDefault(name, name)
                                .toUpperCase(Locale.ROOT)
                        )
                    );
                }
            }
        );
        return set;
    }

    private static String normalise(final String desc) {
        return QueryParserDifferentialTest.SPACES.matcher(desc)
            .replaceAll(" ").trim();
    }
}
