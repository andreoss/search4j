/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API contracts.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of the iterator contracts through stubs.
 *
 * @since 0.1.0
 */
final class IteratorsTest {

    @Test
    void termIteratorAdvances() {
        Assertions.assertEquals("term", new TermStub().next(), "Term value");
    }

    @Test
    void positionIteratorAdvances() {
        Assertions.assertEquals(
            new TermPos(4), new PositionStub().next(), "Position"
        );
    }

    @Test
    void postingIteratorAdvances() {
        Assertions.assertEquals(
            DocId.of(9), new PostingStub().next(), "DocId"
        );
    }

    @Test
    void valueIteratorAdvances() {
        Assertions.assertEquals(
            "value", new ValueStub().next(), "Value text"
        );
    }
}
