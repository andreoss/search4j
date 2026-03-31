/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.common.Strings;
import java.util.Objects;

/**
 * A node opened as one posting list, weighted by a factor.
 *
 * @param node The node, a leaf or an operator not merged into its parent
 * @param factor Weight factor, zero when unweighted
 * @since 0.1.0
 */
record LeafPlan(Node node, double factor) implements Plan {

    /**
     * Creates the plan.
     *
     * @param node The node, a leaf or an operator not merged into its parent
     * @param factor Weight factor, zero when unweighted
     */
    LeafPlan {
        Objects.requireNonNull(node, "node");
    }

    @Override
    public String describe() {
        return String.format(
            "%s:%s", this.node.describe(), Strings.decimal(this.factor)
        );
    }
}
