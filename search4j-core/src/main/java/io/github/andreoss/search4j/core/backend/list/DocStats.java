/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Iterator bases shared by the database backends.
 */
package io.github.andreoss.search4j.core.backend.list;

import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Length statistics of the document a weight is computed for.
 *
 * @param length Sum of the within-document frequencies of its terms
 * @param unique Number of distinct terms
 * @param wdfMax Highest within-document frequency of any term
 * @since 0.1.0
 */
public record DocStats(TermCount length, TermCount unique, TermCount wdfMax) {
}
