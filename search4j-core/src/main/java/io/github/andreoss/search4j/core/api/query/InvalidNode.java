/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.types.QueryOp;

/**
 * The invalid query, a placeholder.
 *
 * @since 0.1.0
 */
enum InvalidNode implements Node {

    /**
     * The single invalid node.
     */
    INSTANCE;

    @Override
    public QueryOp type() {
        return QueryOp.INVALID;
    }

    @Override
    public String describe() {
        return "<INVALID>";
    }
}
