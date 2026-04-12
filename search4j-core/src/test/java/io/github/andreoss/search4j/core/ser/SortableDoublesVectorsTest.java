/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the serialisation utilities.
 */
package io.github.andreoss.search4j.core.ser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks sortable doubles against vectors captured from the reference
 * library: each line names the direction, the input and the output.
 *
 * @since 0.1.0
 */
final class SortableDoublesVectorsTest {

    /**
     * Captured vector field delimiter.
     */
    private static final Pattern SEPARATOR = Pattern.compile("\\t");

    @Test
    void matchesCapturedVectors() throws IOException {
        final List<String> failures = new ArrayList<>(0);
        int count = 0;
        try (
            BufferedReader input = new BufferedReader(
                new InputStreamReader(
                    this.getClass().getResourceAsStream(
                        "sortable-double-vectors.tsv"
                    ),
                    StandardCharsets.UTF_8
                )
            )
        ) {
            String line = input.readLine();
            while (line != null) {
                final String[] vector =
                    SortableDoublesVectorsTest.SEPARATOR.split(line, -1);
                final String actual = SortableDoublesVectorsTest.apply(
                    vector[0], vector[1], vector[2]
                );
                if (!vector[2].equals(actual)) {
                    failures.add(
                        String.format(
                            "%s %s expected %s got %s",
                            vector[0], vector[1], vector[2], actual
                        )
                    );
                }
                count = count + 1;
                line = input.readLine();
            }
        }
        Assertions.assertEquals(
            List.of(), failures, String.format("%d vectors differ", count)
        );
    }

    private static String apply(
        final String direction, final String input, final String expected
    ) {
        final String result;
        if ("serialise".equals(direction)) {
            result = HexFormat.of().formatHex(
                SortableDoubles.serialise(Double.parseDouble(input))
            );
        } else {
            result = SortableDoublesVectorsTest.text(
                expected,
                SortableDoubles.deserialise(HexFormat.of().parseHex(input))
            );
        }
        return result;
    }

    private static String text(final String expected, final double value) {
        String result = Double.toString(value);
        if (Double.compare(Double.parseDouble(expected), value) == 0) {
            result = expected;
        }
        return result;
    }
}
