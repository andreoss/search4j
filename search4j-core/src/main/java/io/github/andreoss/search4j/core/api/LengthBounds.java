/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Bounds on the document lengths of a shard.
 *
 * @param docMin Lower bound on the length of a non-empty document
 * @param docMax Upper bound on the length of a document
 * @param uniqueMin Lower bound on the distinct terms of a non-empty document
 * @since 0.1.0
 */
public record LengthBounds(
    TermCount docMin, TermCount docMax, TermCount uniqueMin
) {
}
