/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Iterator bases shared by the database backends.
 */
package io.github.andreoss.search4j.core.backend.list;

/**
 * Outcome of checking a posting list for one document.
 *
 * @param list List to continue with, the checked one unless pruned
 * @param valid True when the list is on the document or past it; false when
 *  the document is absent and the position is unspecified
 * @since 0.1.0
 */
public record Probe(PostList list, boolean valid) {
}
