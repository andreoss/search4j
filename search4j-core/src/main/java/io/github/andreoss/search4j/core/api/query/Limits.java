/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.error.SerialisationError;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.WildcardLimit;
import io.github.andreoss.search4j.core.types.WildcardPattern;
import java.util.Arrays;

/**
 * The flags byte and combiner byte of serialised expansions: the limit in
 * the two low bits, the pattern features in bits four and five.
 *
 * @since 0.1.0
 */
final class Limits {

    /**
     * Mask of the limit bits.
     */
    private static final int MASK = 0x03;

    /**
     * Utility class, no instances.
     */
    private Limits() {
    }

    /**
     * Limit bits of an expansion limit.
     *
     * @param limit The limit
     * @return Its code: 0 error, 1 first, 2 most frequent
     */
    static int code(final WildcardLimit limit) {
        return switch (limit) {
            case ERROR -> 0;
            case FIRST -> 1;
            case MOST_FREQUENT -> 2;
        };
    }

    /**
     * Expansion limit of a flags byte.
     *
     * @param flags The flags byte
     * @return The limit; the undefined code 3 raises an unsupported-data
     *  error
     */
    static WildcardLimit limit(final int flags) {
        return switch (flags & Limits.MASK) {
            case 0 -> WildcardLimit.ERROR;
            case 1 -> WildcardLimit.FIRST;
            case 2 -> WildcardLimit.MOST_FREQUENT;
            default -> throw new SerialisationError(Cursor.UNSUPPORTED);
        };
    }

    /**
     * Pattern features of a flags byte.
     *
     * @param flags The flags byte
     * @return The features; any other bit raises an unsupported-data error
     */
    static WildcardPattern glob(final int flags) {
        final int bits = flags & ~Limits.MASK;
        return Arrays.stream(WildcardPattern.values())
            .filter(glob -> glob.code() == bits)
            .findFirst()
            .orElseThrow(() -> new SerialisationError(Cursor.UNSUPPORTED));
    }

    /**
     * Combiner of a combiner byte.
     *
     * @param code The combiner byte
     * @return SYNONYM, MAX or OR; any other code raises an unsupported-data
     *  error
     */
    static QueryOp combiner(final int code) {
        final QueryOp combiner;
        if (code == QueryOp.SYNONYM.code()) {
            combiner = QueryOp.SYNONYM;
        } else if (code == QueryOp.MAX.code()) {
            combiner = QueryOp.MAX;
        } else if (code == QueryOp.OR.code()) {
            combiner = QueryOp.OR;
        } else {
            throw new SerialisationError(Cursor.UNSUPPORTED);
        }
        return combiner;
    }
}
