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
 * Term statistics of the query a weight is computed for.
 *
 * @param wqf Within-query frequency
 * @param wdf Within-document frequency
 * @since 0.1.0
 */
public record TermStats(TermCount wqf, TermCount wdf) {
}
