/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

/**
 * Fixed settings of one collection of matches.
 *
 * @param size Number of best matches kept: first plus maximum, at least one
 * @param atleast Number of matching documents to count before stopping
 * @param percent Percentage cutoff, zero for none
 * @param factor Fraction of the best weight a match needs under the
 *  percentage cutoff
 * @param deadline Time in nanoseconds after which counting may stop, zero
 *  for none
 * @param stop Whether the collection ends once the set is full
 * @param ascending Whether sort keys ascend
 * @since 0.4.0
 */
record Limits(
    int size, int atleast, int percent, double factor, long deadline,
    boolean stop, boolean ascending
) {
}
