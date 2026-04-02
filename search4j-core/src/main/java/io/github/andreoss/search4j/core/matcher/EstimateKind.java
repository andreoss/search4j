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
    KNOWN,

    /**
     * Documents present in every child.
     */
    AND,

    /**
     * Left-side documents absent from the right side.
     */
    AND_NOT,

    /**
     * Documents present in one or more children.
     */
    OR,

    /**
     * Documents present in an odd number of children.
     */
    XOR
}
