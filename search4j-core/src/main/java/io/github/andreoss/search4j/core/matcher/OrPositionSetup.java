/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Prepared lists and cursor positions for an OR position merge.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Mutable merge lists prepared from validated sources.
 *
 * @param lists Live source lists
 * @param positions Current position in each live source
 * @since 0.1.0
 */
record OrPositionSetup(List<PositionList> lists, List<TermPos> positions) {

    static OrPositionSetup fresh(final List<? extends PositionList> sources) {
        final List<PositionList> copy = List.copyOf(
            Objects.requireNonNull(sources, "sources")
        );
        return new OrPositionSetup(
            new ArrayList<>(copy), new ArrayList<>(copy.size())
        );
    }
}
