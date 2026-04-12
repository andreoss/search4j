/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Matcher core of the search engine.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.PostList;

/**
 * Posting list tree of a query with the estimate of its matches.
 *
 * @param list Posting list tree
 * @param estimate Estimate of the matches of the tree
 * @since 0.3.0
 */
public record MatchTree(PostList list, MatchEstimate estimate) {
}
