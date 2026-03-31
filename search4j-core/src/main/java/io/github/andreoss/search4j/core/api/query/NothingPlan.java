/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

/**
 * The plan matching no document.
 *
 * @since 0.1.0
 */
enum NothingPlan implements Plan {

    /**
     * The single empty plan.
     */
    INSTANCE;

    @Override
    public String describe() {
        return "NOTHING";
    }
}
