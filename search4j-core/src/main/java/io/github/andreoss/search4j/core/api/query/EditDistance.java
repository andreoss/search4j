/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.types.QueryOp;
import java.util.Objects;

/**
 * A target and how it expands to the terms within an edit distance of it.
 *
 * <p>Start from {@link #of(String)} and change what differs from the
 * defaults: no expansion limit, the synonym combiner, two edits and no
 * fixed prefix.</p>
 *
 * @param target The target
 * @param expansion How far the target expands
 * @param combiner Operator over the expanded terms: {@link QueryOp#SYNONYM},
 *  {@link QueryOp#MAX} or {@link QueryOp#OR}
 * @param edits Most edits between the target and a term
 * @param prefix Bytes at the start of the target a term must match exactly
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public record EditDistance(
    String target, Expansion expansion, QueryOp combiner, int edits, int prefix
) {

    /**
     * Creates the edit distance.
     *
     * @param target The target
     * @param expansion How far the target expands
     * @param combiner Operator over the expanded terms
     * @param edits Most edits between the target and a term, non-negative
     * @param prefix Bytes matched exactly, non-negative
     */
    public EditDistance {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(expansion, "expansion");
        Combiners.check(combiner);
        if (edits < 0 || prefix < 0) {
            throw new IllegalArgumentException(
                String.format(
                    "Edits and prefix must not be negative: %d, %d", edits, prefix
                )
            );
        }
    }

    /**
     * Edit distance with the defaults: unlimited, synonym, two edits, no
     * fixed prefix.
     *
     * @param target The target
     * @return Edit distance over the target
     */
    public static EditDistance of(final String target) {
        return new EditDistance(
            target, Expansion.UNLIMITED, QueryOp.SYNONYM, 2, 0
        );
    }

    /**
     * Same edit distance with another expansion.
     *
     * @param limit How far the target expands
     * @return Edit distance with the change
     */
    public EditDistance withExpansion(final Expansion limit) {
        return new EditDistance(
            this.target, limit, this.combiner, this.edits, this.prefix
        );
    }

    /**
     * Same edit distance with another combiner.
     *
     * @param operator Operator over the expanded terms
     * @return Edit distance with the change
     */
    public EditDistance withCombiner(final QueryOp operator) {
        return new EditDistance(
            this.target, this.expansion, operator, this.edits, this.prefix
        );
    }

    /**
     * Same edit distance with another number of edits.
     *
     * @param count Most edits between the target and a term
     * @return Edit distance with the change
     */
    public EditDistance withEdits(final int count) {
        return new EditDistance(
            this.target, this.expansion, this.combiner, count, this.prefix
        );
    }

    /**
     * Same edit distance with another fixed prefix.
     *
     * @param length Bytes at the start of the target matched exactly
     * @return Edit distance with the change
     */
    public EditDistance withPrefix(final int length) {
        return new EditDistance(
            this.target, this.expansion, this.combiner, this.edits, length
        );
    }
}
