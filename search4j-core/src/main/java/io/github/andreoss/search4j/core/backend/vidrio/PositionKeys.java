/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file, the posting lists and the term
 * lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.ser.SortableStrings;
import io.github.andreoss.search4j.core.ser.SortableUints;
import io.github.andreoss.search4j.core.types.DocId;
import java.io.ByteArrayOutputStream;

/**
 * Keys of the position table: the sort-preserving term with its
 * terminator, then the sort-preserving document number.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class PositionKeys {

    /**
     * Utility class, no instances.
     */
    private PositionKeys() {
    }

    /**
     * Key of the positions of a term in a document.
     *
     * @param doc Document identifier
     * @param term Term text
     * @return Key bytes
     */
    public static byte[] of(final DocId doc, final String term) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(SortableStrings.pack(PostListKeys.bytes(term), false));
        out.writeBytes(SortableUints.pack(doc.value()));
        return out.toByteArray();
    }
}
