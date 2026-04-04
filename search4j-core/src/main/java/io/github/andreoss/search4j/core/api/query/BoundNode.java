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
 * A value slot bounded on one side.
 *
 * @param type Either {@link QueryOp#VALUE_GE} or {@link QueryOp#VALUE_LE}
 * @param slot Value slot compared
 * @param limit Bound of the slot
 * @since 0.1.0
 */
public record BoundNode(QueryOp type, ValueNo slot, String limit) implements Node {

    /**
     * Creates the node.
     *
     * @param type Either {@link QueryOp#VALUE_GE} or {@link QueryOp#VALUE_LE}
     * @param slot Value slot compared
     * @param limit Bound of the slot
     */
    public BoundNode {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(limit, "limit");
    }

    @Override
    public String describe() {
        return String.format(
            "%s %s %s", this.type.name(), this.slot, Description.escape(this.limit)
        );
    }
}
