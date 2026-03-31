/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table and the version file.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Fields of a posting list chunk, decoded into the value types.
 *
 * @since 0.1.0
 */
final class PostingFields {

    /**
     * Utility class, no instances.
     */
    private PostingFields() {
    }

    /**
     * Decodes a count.
     *
     * @param reader Reader at the count
     * @param what Field name for the error
     * @return Count, a corrupt-data error above the range
     */
    static TermCount count(final ByteReader reader, final String what) {
        final long value = reader.narrow(what);
        if (value > Integer.MAX_VALUE) {
            throw new DatabaseCorruptError(
                String.format("%s too large: %d", what, value)
            );
        }
        return new TermCount((int) value);
    }

    /**
     * Document some way after another.
     *
     * @param from Document the gap is counted from
     * @param gap Distance, at least 0
     * @param what Field name for the error
     * @return Document, a corrupt-data error outside the range
     */
    static DocId after(final DocId from, final long gap, final String what) {
        final long value = from.value() + gap;
        if (value < 1L || value > Integer.MAX_VALUE) {
            throw new DatabaseCorruptError(
                String.format("%s out of range: %d", what, value)
            );
        }
        return DocId.of((int) value);
    }
}
