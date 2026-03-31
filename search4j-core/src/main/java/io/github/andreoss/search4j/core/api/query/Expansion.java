/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.WildcardLimit;
import java.util.Objects;

/**
 * How far a pattern expands: the most terms it may expand to and what
 * happens when there are more.
 *
 * @param max Most terms expanded to, zero for no limit
 * @param limit What happens when the limit is exceeded
 * @since 0.1.0
 */
public record Expansion(TermCount max, WildcardLimit limit) {

    /**
     * No limit; the limit action is moot and raises an error.
     */
    public static final Expansion UNLIMITED =
        new Expansion(TermCount.ZERO, WildcardLimit.ERROR);

    /**
     * Creates the expansion.
     *
     * @param max Most terms expanded to, zero for no limit
     * @param limit What happens when the limit is exceeded
     */
    public Expansion {
        Objects.requireNonNull(max, "max");
        Objects.requireNonNull(limit, "limit");
    }
}
