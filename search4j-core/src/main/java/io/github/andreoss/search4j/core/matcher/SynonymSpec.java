/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Posting list operators combining the streams of a query tree.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.error.InvalidArgumentError;

/**
 * Scoring scheme and frequency clamp of a synonym group.
 *
 * @param weight Scheme scoring the group as one term
 * @param clamp Whether the frequency is clamped to the document length
 * @since 0.1.0
 */
record SynonymSpec(SynonymWeight weight, boolean clamp) {
    SynonymSpec {
        if (weight == null) {
            throw new InvalidArgumentError("A scheme is required");
        }
    }
}
