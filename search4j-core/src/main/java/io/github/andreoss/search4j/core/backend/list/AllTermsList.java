/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Iterator bases shared by the database backends.
 */
package io.github.andreoss.search4j.core.backend.list;

import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Term list over every term of a database, or a keyed listing like it.
 *
 * <p>Frequencies and positions within a document have no meaning here and
 * raise an invalid-operation error.</p>
 *
 * @since 0.1.0
 */
public interface AllTermsList extends TermList {

    @Override
    default TermCount wdf() {
        throw new InvalidOperationError(
            "Within-document frequency is not meaningful for a term listing"
        );
    }

    @Override
    default TermCount positionCount() {
        throw new InvalidOperationError(
            "Positions are not meaningful for a term listing"
        );
    }

    @Override
    default PositionList positionList() {
        throw new InvalidOperationError(
            "Positions are not meaningful for a term listing"
        );
    }
}
