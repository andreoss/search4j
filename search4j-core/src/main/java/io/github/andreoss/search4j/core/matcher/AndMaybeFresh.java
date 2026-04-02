/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Posting list operators combining the streams of a query tree.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.PostList;

/**
 * Children and weights supplied to the optional intersection setup.
 *
 * @param left Required side
 * @param right Optional side
 * @param lmax Maximum weight of the required side
 * @param rmax Maximum weight of the optional side
 * @param recalc Callback for maximum-weight changes
 * @since 0.1.0
 */
record AndMaybeFresh(
    PostList left, PostList right,
    double lmax, double rmax, Runnable recalc
) {
}
