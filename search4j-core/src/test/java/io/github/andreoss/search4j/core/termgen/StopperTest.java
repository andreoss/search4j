/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests for the stop word filter.
 */
package io.github.andreoss.search4j.core.termgen;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the stop word filter.
 *
 * @since 0.1.0
 */
final class StopperTest {

    @Test
    void rejectsListedWordIgnoringCase() {
        Assertions.assertTrue(Stopper.of("the", "and").stop("The"));
    }

    @Test
    void keepsUnlistedWord() {
        Assertions.assertFalse(Stopper.of("the").stop("search"));
    }

    @Test
    void keepsShortWord() {
        Assertions.assertFalse(Stopper.of("in").stop("in"));
    }

    @Test
    void keepsWordStartingWithDigit() {
        Assertions.assertFalse(Stopper.of("123").stop("123"));
    }

    @Test
    void holdsLowerCasedWords() {
        Assertions.assertEquals(Set.of("the"), Stopper.of(List.of("The")).words());
    }

    @Test
    void acceptsVarargs() {
        Assertions.assertTrue(new Stopper("one", "two").stop("two"));
    }

    @Test
    void rejectsEmptyTerm() {
        Assertions.assertFalse(Stopper.of("").stop(""));
    }
}
