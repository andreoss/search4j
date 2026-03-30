/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Iterator bases shared by the database backends.
 */
package io.github.andreoss.search4j.core.backend.list;

/**
 * Posting list: the documents matching a term or a combination of lists, as
 * walked by the matcher.
 *
 * @since 0.1.0
 */
public interface PostList extends PostingCursor, PostingScores, PostingPositions {
}
