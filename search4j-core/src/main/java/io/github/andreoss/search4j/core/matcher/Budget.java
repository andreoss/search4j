/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Matcher core of the search engine.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.error.WildcardError;
import io.github.andreoss.search4j.core.types.WildcardLimit;

/**
 * Number of terms an expansion may still take before it stops or fails.
 *
 * @since 0.10.0
 */
final class Budget {

    /**
     * How the limit applies.
     */
    private final WildcardLimit mode;

    /**
     * Message when the terms exceed the limit.
     */
    private final String excess;

    /**
     * Terms still allowed.
     */
    private long left;

    /**
     * Whether the expansion goes on.
     */
    private boolean open;

    private Budget(final WildcardLimit mode, final String excess, final long left) {
        this.mode = mode;
        this.excess = excess;
        this.left = left;
        this.open = true;
    }

    static Budget of(final ExpansionSpec spec) {
        long left = spec.limits().max().value();
        if (left == 0L) {
            left = 0xFFFF_FFFFL;
        }
        return new Budget(spec.limits().limit(), spec.excess(), left);
    }

    boolean open() {
        return this.open;
    }

    boolean take() {
        if (this.mode != WildcardLimit.MOST_FREQUENT) {
            if (this.left == 0L && this.mode != WildcardLimit.FIRST) {
                throw new WildcardError(this.excess);
            }
            this.open = this.left != 0L;
            this.left = this.left - 1L;
        }
        return this.open;
    }
}
