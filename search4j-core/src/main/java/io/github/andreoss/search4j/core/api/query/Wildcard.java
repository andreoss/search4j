/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.WildcardPattern;
import java.util.Objects;

/**
 * A wildcard pattern and how it expands.
 *
 * <p>Start from {@link #of(String)} and change what differs from the
 * defaults: no expansion limit, a literal prefix pattern and the synonym
 * combiner.</p>
 *
 * @param pattern The pattern
 * @param expansion How far the pattern expands
 * @param glob Which glob characters the pattern interprets
 * @param combiner Operator over the expanded terms: {@link QueryOp#SYNONYM},
 *  {@link QueryOp#MAX} or {@link QueryOp#OR}
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public record Wildcard(
    String pattern, Expansion expansion, WildcardPattern glob, QueryOp combiner
) {

    /**
     * Creates the wildcard.
     *
     * @param pattern The pattern
     * @param expansion How far the pattern expands
     * @param glob Which glob characters the pattern interprets
     * @param combiner Operator over the expanded terms
     */
    public Wildcard {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(expansion, "expansion");
        Objects.requireNonNull(glob, "glob");
        Combiners.check(combiner);
    }

    /**
     * Wildcard with the defaults: unlimited, a literal prefix, synonym.
     *
     * @param pattern The pattern
     * @return Wildcard over the pattern
     */
    public static Wildcard of(final String pattern) {
        return new Wildcard(
            pattern, Expansion.UNLIMITED, WildcardPattern.PREFIX,
            QueryOp.SYNONYM
        );
    }

    /**
     * Same wildcard with another expansion.
     *
     * @param limit How far the pattern expands
     * @return Wildcard with the change
     */
    public Wildcard withExpansion(final Expansion limit) {
        return new Wildcard(this.pattern, limit, this.glob, this.combiner);
    }

    /**
     * Same wildcard with other glob characters.
     *
     * @param features Which glob characters the pattern interprets
     * @return Wildcard with the change
     */
    public Wildcard withGlob(final WildcardPattern features) {
        return new Wildcard(
            this.pattern, this.expansion, features, this.combiner
        );
    }

    /**
     * Same wildcard with another combiner.
     *
     * @param operator Operator over the expanded terms
     * @return Wildcard with the change
     */
    public Wildcard withCombiner(final QueryOp operator) {
        return new Wildcard(this.pattern, this.expansion, this.glob, operator);
    }
}
