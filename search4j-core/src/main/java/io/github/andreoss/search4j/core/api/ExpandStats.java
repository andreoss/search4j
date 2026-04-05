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
 * Term statistics of an expansion weight.
 *
 * @param freq Term frequency in the database
 * @param dfreq Document frequency in the matching set
 * @param docs Number of matching documents
 * @since 0.1.0
 */
public record ExpandStats(TermCount freq, TermCount dfreq, TermCount docs) {
}
