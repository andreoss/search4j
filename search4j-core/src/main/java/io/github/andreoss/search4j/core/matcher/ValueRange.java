/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Posting list operators combining the streams of a query tree.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.ValueNo;

/**
 * Slot and inclusive bounds of a value range.
 *
 * @param slot Slot read
 * @param lower Lowest accepted value, inclusive
 * @param upper Highest accepted value, inclusive, empty for no bound
 * @since 0.1.0
 */
record ValueRange(ValueNo slot, String lower, String upper) {

    /**
     * Validates the bounds.
     *
     * @param number Slot read
     * @param low Lowest accepted value
     * @param high Highest accepted value, empty for no bound
     * @return Checked bounds
     */
    static ValueRange of(
        final ValueNo number, final String low, final String high
    ) {
        if (number == null) {
            throw new InvalidArgumentError("A slot is required");
        }
        if (low == null) {
            throw new InvalidArgumentError("A lower bound is required");
        }
        if (high == null) {
            throw new InvalidArgumentError("An upper bound is required");
        }
        if (!high.isEmpty() && low.compareTo(high) > 0) {
            throw new InvalidArgumentError(
                String.format(
                    "Range lower bound %s exceeds upper bound %s",
                    low, high
                )
            );
        }
        return new ValueRange(number, low, high);
    }

    /**
     * Tests whether a value is inside the range.
     *
     * @param value Candidate value
     * @return True when accepted
     */
    boolean accepts(final String value) {
        boolean accepted = value.compareTo(this.lower) >= 0;
        if (accepted && !this.upper.isEmpty()) {
            accepted = value.compareTo(this.upper) <= 0;
        }
        return accepted;
    }

    /**
     * Description naming the slot and both bounds.
     *
     * @return Description of the range
     */
    String describe() {
        return String.format(
            "ValueRangePostList(%s, %s, %s)",
            this.slot.value(), this.lower, this.upper
        );
    }
}
