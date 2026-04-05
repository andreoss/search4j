/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.types.ValueNo;

/**
 * Range processor over one value slot.
 *
 * <p>The bounds are compared as stored value strings, which is the lexical
 * order the value range operator uses.</p>
 *
 * @since 0.1.0
 */
public final class ValueRangeProcessor implements RangeProcessor {

    /**
     * Slot read by the range.
     */
    private final ValueNo slot;

    /**
     * Creates a processor over a slot.
     *
     * @param slot Slot read by the range
     */
    public ValueRangeProcessor(final ValueNo slot) {
        this.slot = slot;
    }

    @Override
    public Query process(final String begin, final String end) {
        final String lower;
        if (begin == null) {
            lower = "";
        } else {
            lower = begin;
        }
        final String upper;
        if (end == null) {
            upper = "";
        } else {
            upper = end;
        }
        return Query.valueRange(this.slot, lower, upper);
    }

    /**
     * Slot read by the range.
     *
     * @return Slot number
     */
    public ValueNo slot() {
        return this.slot;
    }
}
