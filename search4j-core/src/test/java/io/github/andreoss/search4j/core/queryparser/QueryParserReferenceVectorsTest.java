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

import io.github.andreoss.search4j.core.types.QueryOp;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks selected reference-derived parser descriptions.
 *
 * @since 0.1.0
 */
final class QueryParserReferenceVectorsTest {

    /** Captured vector field delimiter. */
    private static final Pattern SEPARATOR =
        Pattern.compile("\\t");

    @Test
    void comparesNativeVectors() throws IOException {
        try (
            BufferedReader input = new BufferedReader(
                new InputStreamReader(
                    getClass().getResourceAsStream("reference-parser-vectors.tsv"),
                    StandardCharsets.UTF_8
                )
            )
        ) {
            String line = input.readLine();
            while (line != null) {
                final String[] vector =
                    QueryParserReferenceVectorsTest.SEPARATOR.split(line, 2);
                final String actual = new QueryParser(
                    new QueryParserTokeniser(vector[0]).tokenise(), QueryOp.AND
                ).parse().description();
                Assertions.assertEquals(vector[1], actual, vector[0]);
                line = input.readLine();
            }
        }
    }
}
