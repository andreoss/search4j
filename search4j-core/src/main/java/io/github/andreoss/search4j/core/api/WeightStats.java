/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

/**
 * Statistics a weighting scheme is prepared with.
 *
 * @param collection Statistics of the collection and query
 * @param term Statistics of the query term
 * @since 0.1.0
 */
public record WeightStats(CollectionStats collection, TermStats term) {
}
