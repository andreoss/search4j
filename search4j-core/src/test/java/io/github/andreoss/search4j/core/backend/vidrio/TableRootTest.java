/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link TableRoot}.
 *
 * @since 0.1.0
 */
final class TableRootTest {

    @Test
    void acceptsHighestLevel() {
        Assertions.assertEquals(
            VidrioFormat.CURSOR_LEVELS - 1,
            TableRootTest.root(VidrioFormat.CURSOR_LEVELS - 1, false).level(),
            "Highest level"
        );
    }

    @Test
    void createsEmptyRoot() {
        Assertions.assertEquals(
            TableRootTest.root(0, true),
            TableRoot.empty(BlockSize.MIN, Trees.REVISION),
            "Fake root"
        );
    }

    @Test
    void rejectsBadLevels() {
        for (final int level : new int[] {-1, VidrioFormat.CURSOR_LEVELS}) {
            Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> TableRootTest.root(level, false),
                String.format("Level %d", level)
            );
        }
    }

    @Test
    void rejectsFakeRootAboveLeaves() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> TableRootTest.root(1, true),
            "Fake root is a leaf"
        );
    }

    private static TableRoot root(final int level, final boolean fake) {
        return new TableRoot(
            new BlockNumber(0L), level, BlockSize.MIN, Trees.REVISION, fake
        );
    }
}
