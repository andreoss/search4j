/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.backend.TermOrder;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.Comparator;

/**
 * A term of a query at its query position.
 *
 * @param pos Query position of the term
 * @param term The term
 * @since 0.1.0
 */
record TermAt(TermPos pos, String term) {

    /**
     * Ordering by position, then by term.
     */
    static final Comparator<TermAt> POSITIONAL = Comparator
        .comparing(TermAt::pos)
        .thenComparing(TermAt::term, TermOrder.INSTANCE);

    /**
     * Ordering by term alone.
     */
    static final Comparator<TermAt> BY_TERM =
        Comparator.comparing(TermAt::term, TermOrder.INSTANCE);
}
