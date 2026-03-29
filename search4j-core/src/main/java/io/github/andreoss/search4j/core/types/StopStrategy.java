/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Stop-word strategy enumeration.
 */
package io.github.andreoss.search4j.core.types;

/**
 * Stop-word strategy for the term generator.
 *
 * <p>Declared in the order of the stopping strategies.</p>
 *
 * @since 0.1.0
 */
public enum StopStrategy {

    /**
     * Do not remove stop words.
     */
    NONE,

    /**
     * Remove stop words.
     */
    ALL,

    /**
     * Remove stop words after stemming them.
     */
    STEMMED
}
