/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.List;

/**
 * Immutable occurrence of a term in a stored document.
 *
 * @param wdf The within-document frequency
 * @param positions The positions in ascending order
 * @since 0.1.0
 */
record Occurrence(TermCount wdf, List<TermPos> positions) {

    /**
     * Creates an occurrence holding a copy of the positions.
     *
     * @param wdf The within-document frequency
     * @param positions The positions in ascending order
     */
    Occurrence {
        positions = List.copyOf(positions);
    }
}
