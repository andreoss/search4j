/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

/**
 * Glob patterns that match every term.
 *
 * @since 0.1.0
 */
final class Globs {

    /**
     * Utility class, no instances.
     */
    private Globs() {
    }

    /**
     * Tests whether a pattern is one or more interpreted asterisks with at
     * most one interpreted question mark, in any order.
     *
     * @param spec Wildcard with a non-empty pattern
     * @return True when the pattern matches every term
     */
    static boolean everything(final Wildcard spec) {
        final String pattern = spec.pattern();
        boolean all = false;
        boolean mark = false;
        for (int index = 0; index < pattern.length(); ++index) {
            final char chr = pattern.charAt(index);
            if (chr == '*' && spec.glob().multi()) {
                all = true;
            } else if (chr == '?' && !mark && spec.glob().single()) {
                mark = true;
            } else {
                all = false;
                break;
            }
        }
        return all;
    }
}
