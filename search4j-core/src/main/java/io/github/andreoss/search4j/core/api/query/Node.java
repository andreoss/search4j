/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.stream.Stream;

/**
 * One node of a query tree; a match-nothing query has none.
 *
 * @since 0.1.0
 */
sealed interface Node permits TermNode, ScaleNode, BoundNode, RangeNode,
    WildcardNode, EditNode, InvalidNode, BranchNode {

    /**
     * Operator or leaf type of the node.
     *
     * @return Type of the node
     */
    QueryOp type();

    /**
     * Description of the node without the outer wrapper.
     *
     * @return Description text
     */
    String describe();

    /**
     * Number of subqueries of the node.
     *
     * @return Count, zero for a leaf
     */
    default int subqueryCount() {
        return 0;
    }

    /**
     * One subquery of the node.
     *
     * @param index Index below the count
     * @return Subquery at the index
     */
    default Query subquery(final int index) {
        throw new InvalidArgumentError(
            "subquery() not meaningful for this query"
        );
    }

    /**
     * Within-query frequency of a leaf.
     *
     * @return Frequency of the term
     */
    default TermCount wqf() {
        throw new InvalidArgumentError("wqf() not meaningful for this query");
    }

    /**
     * Query position of a leaf.
     *
     * @return Position of the term
     */
    default TermPos pos() {
        throw new InvalidArgumentError("pos() not meaningful for this query");
    }

    /**
     * Length of the node: the sum of the frequencies of its terms.
     *
     * @return Length, zero when no term contributes
     */
    default TermCount length() {
        return TermCount.ZERO;
    }

    /**
     * Terms of the node with their positions, in tree order.
     *
     * @return Positioned terms, empty for nodes without terms
     */
    default Stream<TermAt> terms() {
        return Stream.empty();
    }
}
