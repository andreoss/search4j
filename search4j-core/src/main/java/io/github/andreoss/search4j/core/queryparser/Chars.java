/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query parser grammar and parsing.
 */
package io.github.andreoss.search4j.core.queryparser;

/**
 * Character classes of the query lexer.
 *
 * @since 0.1.0
 */
final class Chars {

    /**
     * Marker of a character dropped from a word.
     */
    static final int IGNORE = -1;

    /**
     * Utility class, no instances.
     */
    private Chars() {
    }

    /**
     * Tests for a letter, mark, number or connector.
     *
     * @param chr Code point
     * @return True when the code point belongs to a word
     */
    static boolean word(final int chr) {
        return switch (Character.getType(chr)) {
            case Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER,
                Character.TITLECASE_LETTER, Character.MODIFIER_LETTER,
                Character.OTHER_LETTER, Character.NON_SPACING_MARK,
                Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK,
                Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER,
                Character.OTHER_NUMBER, Character.CONNECTOR_PUNCTUATION -> true;
            default -> false;
        };
    }

    /**
     * Tests for a space, separator or control character.
     *
     * @param chr Code point
     * @return True for white space
     */
    static boolean space(final int chr) {
        return switch (Character.getType(chr)) {
            case Character.SPACE_SEPARATOR, Character.LINE_SEPARATOR,
                Character.PARAGRAPH_SEPARATOR, Character.CONTROL -> true;
            default -> false;
        };
    }

    /**
     * Tests for a decimal digit in any script.
     *
     * @param chr Code point
     * @return True for a decimal digit
     */
    static boolean digit(final int chr) {
        return Character.getType(chr) == Character.DECIMAL_DIGIT_NUMBER;
    }

    /**
     * Tests for an ASCII digit.
     *
     * @param chr Code point
     * @return True for 0 to 9
     */
    static boolean ascii(final int chr) {
        return chr >= '0' && chr <= '9';
    }

    /**
     * Tests for an ASCII capital letter.
     *
     * @param chr Code point
     * @return True for A to Z
     */
    static boolean capital(final int chr) {
        return chr >= 'A' && chr <= 'Z';
    }

    /**
     * Tests for a double quote, straight or curly.
     *
     * @param chr Code point
     * @return True for a double quote
     */
    static boolean quote(final int chr) {
        return chr == '"' || chr == 0x201C || chr == 0x201D;
    }

    /**
     * Tests for a character joining words into a phrase.
     *
     * @param chr Code point
     * @return True for a phrase generator
     */
    static boolean generator(final int chr) {
        return chr > 0 && chr < 128 && ".-/:\\@".indexOf(chr) >= 0;
    }

    /**
     * Tests for a character that prevents stemming of the word before it.
     *
     * @param chr Code point
     * @return True for a stem preventer
     */
    static boolean preventer(final int chr) {
        return chr > 0 && chr < 128 && "(/\\@<>=*[{\"".indexOf(chr) >= 0;
    }

    /**
     * Tests for a suffix character such as the plus signs of a language name.
     *
     * @param chr Code point
     * @return True for a suffix character
     */
    static boolean suffix(final int chr) {
        return chr == '+' || chr == '#';
    }

    /**
     * Maps a character between two word characters.
     *
     * @param chr Code point
     * @return The character kept, {@link #IGNORE}, or zero to end the word
     */
    static int infix(final int chr) {
        final int result;
        if (chr == '\'' || chr == '&' || Chars.joiner(chr)) {
            result = chr;
        } else if (chr == 0x2019 || chr == 0x201B) {
            result = '\'';
        } else if (Chars.invisible(chr) || chr == 0xAD) {
            result = Chars.IGNORE;
        } else {
            result = 0;
        }
        return result;
    }

    /**
     * Maps a character between two digits.
     *
     * @param chr Code point
     * @return The character kept, {@link #IGNORE}, or zero to end the word
     */
    static int digitInfix(final int chr) {
        final int result;
        if (",.;".indexOf(chr) >= 0 || Chars.separator(chr)) {
            result = chr;
        } else if (Chars.invisible(chr) || chr == 0x200B) {
            result = Chars.IGNORE;
        } else {
            result = 0;
        }
        return result;
    }

    private static boolean joiner(final int chr) {
        return switch (chr) {
            case 0xB7, 0x5F4, 0x2027 -> true;
            default -> false;
        };
    }

    private static boolean separator(final int chr) {
        return switch (chr) {
            case 0x37E, 0x589, 0x60D, 0x7F8, 0x2044, 0xFE10, 0xFE13, 0xFE14 -> true;
            default -> false;
        };
    }

    private static boolean invisible(final int chr) {
        return chr == 0x200C || chr == 0x200D || chr == 0x2060
            || chr == 0xFEFF;
    }
}
