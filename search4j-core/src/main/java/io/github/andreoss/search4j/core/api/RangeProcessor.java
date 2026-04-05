/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.api.query.Query;

/**
 * Builds a query from a pair of range bounds.
 *
 * <p>The query parser calls a processor with the text on each side of the
 * range operator and uses the returned query, or falls back to a plain term
 * when the processor declines.</p>
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface RangeProcessor {

    /**
     * Builds a query from the bounds of a range.
     *
     * @param begin Text before the range operator, empty when absent
     * @param end Text after the range operator, empty when absent
     * @return Query for the range
     */
    Query process(String begin, String end);
}
