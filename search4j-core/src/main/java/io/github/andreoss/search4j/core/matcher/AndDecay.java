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
 * Weighted children supplied to the decayed intersection setup.
 *
 * @param left First member
 * @param right Second member
 * @param lmax Maximum weight of the first member
 * @param rmax Maximum weight of the second member
 * @since 0.1.0
 */
record AndDecay(
    PostList left, PostList right, double lmax, double rmax
) {
}
