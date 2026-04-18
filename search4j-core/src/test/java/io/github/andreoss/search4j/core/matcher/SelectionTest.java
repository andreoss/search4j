/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the matcher core.
 */
package io.github.andreoss.search4j.core.matcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the partial ordering against permutations captured from the
 * reference standard library for keys with many ties.
 *
 * @since 0.10.0
 */
final class SelectionTest {

    @Test
    void permutesAsTheCapturedSelections() throws IOException {
        final List<String> failures = new ArrayList<>(0);
        final List<String> lines = SelectionTest.lines();
        for (final String line : lines) {
            final String[] row = line.split("\t", -1);
            final int rank = Integer.parseInt(row[0]);
            final String[] keys = row[1].split(",", -1);
            final List<int[]> items = new ArrayList<>(keys.length);
            for (int index = 0; index < keys.length; ++index) {
                items.add(new int[] {Integer.parseInt(keys[index]), index});
            }
            new Selection<int[]>(items, (left, right) -> left[0] > right[0])
                .nth(0, rank - 1, items.size());
            final StringBuilder order = new StringBuilder(64);
            for (final int[] item : items) {
                if (order.length() > 0) {
                    order.append(',');
                }
                order.append(item[1]);
            }
            if (!order.toString().equals(row[2])) {
                failures.add(String.format("%s got %s", line, order));
            }
        }
        Assertions.assertEquals(
            List.of(400, List.of()), List.of(lines.size(), failures),
            "Positions of the elements after selecting each rank"
        );
    }

    @Test
    void leavesAnEmptyRangeAlone() {
        final List<Integer> items = new ArrayList<>(List.of(3, 1, 2));
        new Selection<Integer>(items, (left, right) -> left > right).nth(1, 1, 1);
        new Selection<Integer>(items, (left, right) -> left > right).nth(0, 3, 3);
        Assertions.assertEquals(List.of(3, 1, 2), items, "Untouched elements");
    }

    private static List<String> lines() throws IOException {
        try (
            BufferedReader input = new BufferedReader(
                new InputStreamReader(
                    SelectionTest.class.getResourceAsStream("selection-vectors.tsv"),
                    StandardCharsets.UTF_8
                )
            )
        ) {
            return input.lines().toList();
        }
    }
}
