/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import java.util.HashMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link Spine}.
 *
 * @since 0.1.0
 */
final class SpineTest {

    @Test
    void rejectsTreeOfTooManyLevels() {
        final MemoryBlocks blocks = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        final Spine spine =
            new Spine(blocks, FreeBlocks.of(blocks, FreeListInfo.EMPTY));
        spine.open(TableRoot.empty(BlockSize.MIN, new RevisionNumber(0L)));
        for (int level = 1; level < VidrioFormat.CURSOR_LEVELS; ++level) {
            spine.grow(Page.fresh(BlockSize.MIN.bytes(), 1L, level), level);
        }
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> spine.grow(Page.fresh(BlockSize.MIN.bytes(), 1L, 10), 10L),
            "Ten levels"
        );
    }
}
