/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the serialisation utilities.
 */
package io.github.andreoss.search4j.core.ser;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link Keywords}.
 *
 * @since 0.1.0
 */
final class KeywordsTest {

    @Test
    void leavesAlphanumericUntouched() {
        Assertions.assertEquals(
            "abc123", Keywords.escape("abc123"), "Alphanumeric pass-through"
        );
    }

    @Test
    void escapesSpecialCharacters() {
        Assertions.assertEquals(
            "a%20b", Keywords.escape("a b"), "Space escaped"
        );
    }

    @Test
    void roundTripsText() {
        Assertions.assertEquals(
            "a b-c", Keywords.unescape(Keywords.escape("a b-c")), "Round trip"
        );
    }
}
