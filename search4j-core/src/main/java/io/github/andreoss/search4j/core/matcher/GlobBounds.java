/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Matcher core of the search engine.
 */
package io.github.andreoss.search4j.core.matcher;

/**
 * Lengths of the terms a wildcard pattern may match.
 *
 * @param shortest Shortest matching term
 * @param longest Longest matching term
 * @param check Length from which the variable part must be checked
 * @since 0.10.0
 */
record GlobBounds(long shortest, long longest, long check) {
}
