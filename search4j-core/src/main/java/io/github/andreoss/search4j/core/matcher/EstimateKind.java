/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Operation represented by a matcher estimate node.
 */
package io.github.andreoss.search4j.core.matcher;

/**
 * Boolean estimate operations supported by the matcher.
 *
 * @since 0.1.0
 */
enum EstimateKind {

    /**
     * A leaf with known match bounds.
     */
    KNOWN(1),

    /**
     * Documents of a child with terms near each other.
     *
     * @since 0.10.0
     */
    NEAR(2),

    /**
     * Documents of a child with terms in order within a window.
     *
     * @since 0.10.0
     */
    PHRASE(3),

    /**
     * Documents of a child with terms at adjacent positions.
     *
     * @since 0.10.0
     */
    EXACT_PHRASE(4),

    /**
     * Documents present in every child.
     */
    AND(1),

    /**
     * Left-side documents absent from the right side.
     */
    AND_NOT(1),

    /**
     * Documents present in one or more children.
     */
    OR(1),

    /**
     * Documents present in an odd number of children.
     */
    XOR(1);

    /**
     * Divisor of the child estimate of a filter without counts.
     */
    private final int divisor;

    EstimateKind(final int divisor) {
        this.divisor = divisor;
    }

    int divisor() {
        return this.divisor;
    }
}
