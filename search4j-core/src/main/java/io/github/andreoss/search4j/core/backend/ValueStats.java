/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Frequency and bounds of the values held in one slot.
 *
 * @param frequency Number of documents holding a value in the slot
 * @param lower Lower bound on the values, empty when the slot is unused
 * @param upper Upper bound on the values, empty when the slot is unused
 * @since 0.1.0
 */
public record ValueStats(TermCount frequency, String lower, String upper) {

    /**
     * Statistics of an unused slot.
     */
    public static final ValueStats NONE =
        new ValueStats(TermCount.ZERO, "", "");
}
