/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.unicode.Description;
import java.util.Objects;

/**
 * Expansion to the terms within an edit distance of a target.
 *
 * @param spec The target and how it expands
 * @since 0.1.0
 */
record EditNode(EditDistance spec) implements Node {

    /**
     * Creates the node.
     *
     * @param spec The target and how it expands
     */
    EditNode {
        Objects.requireNonNull(spec, "spec");
    }

    @Override
    public QueryOp type() {
        return QueryOp.EDIT_DISTANCE;
    }

    @Override
    public String describe() {
        final StringBuilder out = new StringBuilder(64)
            .append("EDIT_DISTANCE ")
            .append(this.spec.combiner().name())
            .append(' ')
            .append(Description.escape(this.spec.target()))
            .append('~')
            .append(this.spec.edits());
        if (this.spec.prefix() != 0) {
            out.append(" fixed_prefix_len=").append(this.spec.prefix());
        }
        return out.toString();
    }

    @Override
    public TermCount length() {
        return new TermCount(1);
    }
}
