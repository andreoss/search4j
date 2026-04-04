/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Prepared source and estimate for an OR position wrapper.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.Objects;

/**
 * Validated source and retained frequency estimate.
 *
 * @param source Wrapped disjunction
 * @param frequency Estimated number of matching documents
 * @since 0.1.0
 */
record OrPosSetup(PostList source, TermCount frequency) {

    static OrPosSetup fresh(final PostList source) {
        final PostList checked = Objects.requireNonNull(source, "source");
        return new OrPosSetup(checked, checked.termFrequency());
    }
}
