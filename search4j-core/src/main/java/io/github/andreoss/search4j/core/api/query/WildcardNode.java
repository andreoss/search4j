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
 * Expansion of a wildcard pattern.
 *
 * @param spec The pattern and how it expands
 * @since 0.1.0
 */
public record WildcardNode(Wildcard spec) implements Node {

    /**
     * Creates the node.
     *
     * @param spec The pattern and how it expands
     */
    public WildcardNode {
        Objects.requireNonNull(spec, "spec");
    }

    @Override
    public QueryOp type() {
        return QueryOp.WILDCARD;
    }

    @Override
    public String describe() {
        return String.format(
            "WILDCARD %s %s", this.spec.combiner().name(),
            Description.escape(this.spec.pattern())
        );
    }

    @Override
    public TermCount length() {
        return new TermCount(1);
    }
}
