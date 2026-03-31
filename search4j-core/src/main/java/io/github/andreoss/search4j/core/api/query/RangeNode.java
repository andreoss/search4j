/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.ValueNo;
import io.github.andreoss.search4j.core.unicode.Description;
import java.util.Objects;

/**
 * A value slot bounded on both sides.
 *
 * @param slot Value slot compared
 * @param lower Lower bound, non-empty
 * @param upper Upper bound, not below the lower one
 * @since 0.1.0
 */
record RangeNode(ValueNo slot, String lower, String upper) implements Node {

    /**
     * Creates the node.
     *
     * @param slot Value slot compared
     * @param lower Lower bound, non-empty
     * @param upper Upper bound, not below the lower one
     */
    RangeNode {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(lower, "lower");
        Objects.requireNonNull(upper, "upper");
    }

    @Override
    public QueryOp type() {
        return QueryOp.VALUE_RANGE;
    }

    @Override
    public String describe() {
        return String.format(
            "VALUE_RANGE %s %s %s", this.slot,
            Description.escape(this.lower), Description.escape(this.upper)
        );
    }
}
