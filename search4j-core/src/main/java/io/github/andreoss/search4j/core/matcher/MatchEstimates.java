/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Bounds and estimate for a matcher operation.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Match-count bounds and document-id bounds resolved for a database.
 *
 * @param min Minimum number of matches
 * @param est Estimated number of matches
 * @param max Maximum number of matches
 * @param first Lowest possible matching document
 * @param last Highest possible matching document
 * @since 0.1.0
 */
public record MatchEstimates(
    TermCount min, TermCount est, TermCount max, DocId first, DocId last
) {
}
