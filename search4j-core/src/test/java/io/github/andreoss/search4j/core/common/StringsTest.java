/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the common utilities.
 */
package io.github.andreoss.search4j.core.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link Strings}.
 *
 * @since 0.1.0
 */
final class StringsTest {

    @Test
    void nullIsEmpty() {
        Assertions.assertTrue(Strings.empty(null), "Null is empty");
    }

    @Test
    void blankIsEmpty() {
        Assertions.assertTrue(Strings.empty(""), "Blank is empty");
    }

    @Test
    void textIsNotEmpty() {
        Assertions.assertFalse(Strings.empty("x"), "Text is not empty");
    }

    @Test
    void nullBecomesEmptyString() {
        Assertions.assertEquals("", Strings.orEmpty(null), "Null map");
    }

    @Test
    void textPassesThrough() {
        Assertions.assertEquals("y", Strings.orEmpty("y"), "Text map");
    }
}
