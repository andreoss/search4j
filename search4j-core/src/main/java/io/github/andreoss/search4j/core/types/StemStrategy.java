/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Stemming strategy enumeration.
 */
package io.github.andreoss.search4j.core.types;

/**
 * Stemming strategy for the query parser and the term generator.
 *
 * <p>Declared in the order of the stemming strategies.</p>
 *
 * @since 0.1.0
 */
public enum StemStrategy {

    /**
     * Do not stem at all.
     */
    NONE,

    /**
     * Stem some terms, as selected by capitals or prefixes.
     */
    SOME,

    /**
     * Stem every term.
     */
    ALL,

    /**
     * Stem every term and mark the stem with a trailing Z.
     */
    ALL_Z,

    /**
     * Stem some terms while keeping full positional information.
     */
    SOME_FULL_POS
}
