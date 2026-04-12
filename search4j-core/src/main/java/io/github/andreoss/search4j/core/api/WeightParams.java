/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cursor over the parameter string of a weighting scheme.
 *
 * @since 0.1.0
 */
final class WeightParams {

    /**
     * A number as the reference parses it: no leading plus, no hexadecimal.
     */
    private static final Pattern NUMBER = Pattern.compile(
        "[ \\t\\n\\x0B\\f\\r]*(-?(?:(?i:infinity|inf|nan(?:\\([0-9A-Za-z_]*\\))?)|(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?))"
    );

    /**
     * Name of the scheme, for messages.
     */
    private final String scheme;

    /**
     * The whole parameter string.
     */
    private final String text;

    /**
     * Index of the next unread character.
     */
    private int pos;

    /**
     * Creates a cursor at the start of a parameter string.
     *
     * @param scheme Name of the scheme
     * @param text Parameter string
     */
    WeightParams(final String scheme, final String text) {
        this.scheme = scheme;
        this.text = text;
    }

    /**
     * Whether every character was read.
     *
     * @return True at the end
     */
    boolean done() {
        return this.pos >= this.text.length();
    }

    /**
     * Reads a number.
     *
     * @param message Message of the error raised when there is none
     * @return The number
     */
    double number(final String message) {
        final Matcher found = WeightParams.NUMBER.matcher(this.text)
            .region(this.pos, this.text.length());
        if (!found.lookingAt()) {
            throw this.error(message);
        }
        final double value = WeightParams.value(found.group(1));
        if (WeightParams.overflows(found.group(1), value)) {
            throw this.error(message);
        }
        this.pos = found.end();
        return value;
    }

    /**
     * Reads a number unless every character was read.
     *
     * @param fallback Value when every character was read
     * @param message Message of the error raised when there is none
     * @return The number or the fallback
     */
    double optional(final double fallback, final String message) {
        double value = fallback;
        if (!this.done()) {
            value = this.number(message);
        }
        return value;
    }

    /**
     * Reads a name up to the next space, skipping that space.
     *
     * @return The name, empty when the next character is a space or none
     */
    String name() {
        int end = this.text.indexOf(' ', this.pos);
        if (end < 0) {
            end = this.text.length();
        }
        final String name = this.text.substring(this.pos, end);
        if (!name.isEmpty()) {
            this.pos = Math.min(end + 1, this.text.length());
        }
        return name;
    }

    /**
     * Fails unless every character was read.
     *
     * @param message Message of the error raised
     */
    void end(final String message) {
        if (!this.done()) {
            throw this.error(message);
        }
    }

    /**
     * Parses the only parameter of a scheme.
     *
     * @param scheme Name of the scheme
     * @param params Parameter string, empty for the fallback
     * @param fallback Default value
     * @return The parameter
     */
    static double single(
        final String scheme, final String params, final double fallback
    ) {
        double value = fallback;
        if (!params.isEmpty()) {
            final WeightParams input = new WeightParams(scheme, params);
            value = input.number("Parameter is invalid");
            input.end("Extra data after parameter");
        }
        return value;
    }

    /**
     * Error naming the scheme and the whole parameter string.
     *
     * @param message Leading message
     * @return The error
     */
    InvalidArgumentError error(final String message) {
        final StringBuilder out = new StringBuilder(message)
            .append(": '").append(this.scheme);
        if (!this.text.isEmpty()) {
            out.append(' ').append(this.text);
        }
        return new InvalidArgumentError(out.append('\'').toString());
    }

    private static double value(final String number) {
        final String lower = number.toLowerCase(Locale.ROOT);
        final boolean negative = lower.charAt(0) == '-';
        final double value;
        if (lower.contains("inf")) {
            if (negative) {
                value = Double.NEGATIVE_INFINITY;
            } else {
                value = Double.POSITIVE_INFINITY;
            }
        } else if (lower.contains("nan")) {
            if (negative) {
                value = Double.longBitsToDouble(0xFFF8_0000_0000_0000L);
            } else {
                value = Double.NaN;
            }
        } else {
            value = Double.parseDouble(number);
        }
        return value;
    }

    private static boolean overflows(final String number, final double value) {
        return !number.toLowerCase(Locale.ROOT).matches("-?(?:inf|nan).*")
            && (Double.isInfinite(value)
            || value == 0.0 && number.replaceAll("[eE].*", "").matches(".*[1-9].*"));
    }
}
