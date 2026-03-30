/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the backend contract and support types.
 */
package io.github.andreoss.search4j.core.backend;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link TermOrder}.
 *
 * @since 0.1.0
 */
final class TermOrderTest {

    @Test
    void comparesCodePoints() {
        Assertions.assertTrue(
            TermOrder.INSTANCE.compare("￿", "𐀀") < 0,
            "Supplementary after BMP"
        );
    }

    @Test
    void putsPrefixFirst() {
        Assertions.assertTrue(
            TermOrder.INSTANCE.compare("ab", "abc") < 0, "Prefix first"
        );
    }

    @Test
    void putsLongerLast() {
        Assertions.assertTrue(
            TermOrder.INSTANCE.compare("abc", "ab") > 0, "Longer last"
        );
    }

    @Test
    void findsEqual() {
        Assertions.assertEquals(
            0, TermOrder.INSTANCE.compare("same", "same"), "Equal"
        );
    }

    @Test
    void sortsList() {
        final List<String> sorted = new ArrayList<>(
            List.of("b", "😀", "a", "ａ")
        );
        sorted.sort(TermOrder.INSTANCE);
        Assertions.assertEquals(
            List.of("a", "b", "ａ", "😀"), sorted, "Sorted"
        );
    }
}
