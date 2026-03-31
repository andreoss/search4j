/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

/**
 * A query flattened into the contexts the matcher turns into posting lists.
 *
 * @since 0.1.0
 */
sealed interface Plan permits NothingPlan, LeafPlan, PositionsPlan,
    SynonymPlan, AndPlan, OrPlan {

    /**
     * Description of the plan.
     *
     * @return Description text
     */
    String describe();
}
