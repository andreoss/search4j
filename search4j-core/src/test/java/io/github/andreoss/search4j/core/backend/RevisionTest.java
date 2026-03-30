/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the backend contract and support types.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.types.TermCount;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link Revision}, {@link ValueStats} and
 * {@link TermFrequencies}.
 *
 * @since 0.1.0
 */
final class RevisionTest {

    @Test
    void acceptsZero() {
        Assertions.assertEquals("0", new Revision(0L).toString(), "Zero");
    }

    @Test
    void rejectsNegative() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new Revision(-1L),
            "Negative"
        );
    }

    @Test
    void ordersByNumber() {
        Assertions.assertEquals(
            List.of(-1, 1, 0),
            List.of(
                Integer.signum(new Revision(2L).compareTo(new Revision(10L))),
                Integer.signum(new Revision(10L).compareTo(new Revision(2L))),
                new Revision(5L).compareTo(new Revision(5L))
            ),
            "Numeric order"
        );
    }

    @Test
    void hasEmptyValueStatistics() {
        Assertions.assertEquals(
            new ValueStats(TermCount.ZERO, "", ""), ValueStats.NONE, "Unused"
        );
    }

    @Test
    void hasEmptyFrequencies() {
        Assertions.assertEquals(
            new TermFrequencies(TermCount.ZERO, TermCount.ZERO),
            TermFrequencies.NONE,
            "Unknown term"
        );
    }
}
