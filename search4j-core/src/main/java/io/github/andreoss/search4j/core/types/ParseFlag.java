/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query parser flag enumeration.
 */
package io.github.andreoss.search4j.core.types;

import java.util.Set;

/**
 * Syntax features the query parser recognises.
 *
 * @since 0.1.0
 */
public enum ParseFlag {

    /**
     * Boolean operators and brackets.
     */
    BOOLEAN,

    /**
     * Quoted phrases.
     */
    PHRASE,

    /**
     * Leading plus and minus on terms.
     */
    LOVEHATE,

    /**
     * Boolean operators in any letter case.
     */
    BOOLEAN_ANY_CASE,

    /**
     * Trailing asterisk as a wildcard.
     */
    WILDCARD,

    /**
     * A leading NOT applied to every document.
     */
    PURE_NOT,

    /**
     * The final term matched as a prefix too.
     */
    PARTIAL,

    /**
     * Spelling correction, inert without a spelling source.
     */
    SPELLING,

    /**
     * Tilde before a term for synonym expansion.
     */
    SYNONYM,

    /**
     * Synonym expansion of every single term.
     */
    AUTO_SYNONYMS,

    /**
     * Synonym expansion of every term group.
     */
    GROUP_SYNONYMS,

    /**
     * Parse state kept between calls, inert here.
     */
    ACCUMULATE,

    /**
     * Positional operators replaced by conjunctions.
     */
    NO_POSITIONS,

    /**
     * Capitalised words stemmed as well.
     */
    STEM_CAPITALS;

    /**
     * Flags in effect when none are given.
     */
    public static final Set<ParseFlag> DEFAULTS = Set.of(
        ParseFlag.BOOLEAN, ParseFlag.PHRASE, ParseFlag.LOVEHATE
    );

    /**
     * Flags kept when a query is parsed again after a syntax error.
     */
    public static final Set<ParseFlag> RETAINED = Set.of(
        ParseFlag.NO_POSITIONS, ParseFlag.STEM_CAPITALS
    );
}
