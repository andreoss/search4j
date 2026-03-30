/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import java.util.Comparator;

/**
 * Ordering of term text by Unicode code point, which matches the byte order
 * of the UTF-8 encoding.
 *
 * @since 0.1.0
 */
public enum TermOrder implements Comparator<String> {

    /**
     * The single ordering.
     */
    INSTANCE;

    @Override
    public int compare(final String left, final String right) {
        int result = 0;
        int lpos = 0;
        int rpos = 0;
        while (result == 0 && lpos < left.length() && rpos < right.length()) {
            final int lchar = left.codePointAt(lpos);
            final int rchar = right.codePointAt(rpos);
            result = Integer.compare(lchar, rchar);
            lpos += Character.charCount(lchar);
            rpos += Character.charCount(rchar);
        }
        if (result == 0) {
            result = Integer.compare(left.length() - lpos, right.length() - rpos);
        }
        return result;
    }
}
