/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Wildcard pattern feature enumeration.
 */
package io.github.andreoss.search4j.core.types;

/**
 * Which glob characters a wildcard pattern interprets.
 *
 * <p>Without any, the pattern matches terms starting with it taken
 * literally. The codes are the pattern bits of the wildcard flags word.</p>
 *
 * @since 0.1.0
 */
public enum WildcardPattern {

    /**
     * The pattern is a literal prefix.
     */
    PREFIX(0),

    /**
     * An asterisk matches zero or more characters.
     */
    MULTI(0x10),

    /**
     * A question mark matches exactly one character.
     */
    SINGLE(0x20),

    /**
     * Both the asterisk and the question mark are interpreted.
     */
    GLOB(0x30);

    /**
     * The pattern bits.
     */
    private final int code;

    /**
     * Creates the feature set.
     *
     * @param code The pattern bits
     */
    WildcardPattern(final int code) {
        this.code = code;
    }

    /**
     * Pattern bits of the feature set.
     *
     * @return Bits of the wildcard flags word
     */
    public int code() {
        return this.code;
    }

    /**
     * Tests whether an asterisk matches zero or more characters.
     *
     * @return True for {@link #MULTI} and {@link #GLOB}
     */
    public boolean multi() {
        return (this.code & WildcardPattern.MULTI.code) != 0;
    }

    /**
     * Tests whether a question mark matches exactly one character.
     *
     * @return True for {@link #SINGLE} and {@link #GLOB}
     */
    public boolean single() {
        return (this.code & WildcardPattern.SINGLE.code) != 0;
    }
}
