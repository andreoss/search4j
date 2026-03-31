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

import io.github.andreoss.search4j.core.ser.SortableUints;
import io.github.andreoss.search4j.core.types.DocId;

/**
 * Keys of the term list table: the sort-preserving document number, packed
 * as the document data keys are.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class TermListKeys {

    /**
     * Utility class, no instances.
     */
    private TermListKeys() {
    }

    /**
     * Key of a document's term list.
     *
     * @param doc Document identifier
     * @return Key bytes
     */
    public static byte[] of(final DocId doc) {
        return SortableUints.pack(doc.value());
    }

    /**
     * Document identifier of a term list key.
     *
     * @param key Key from {@link #of(DocId)}
     * @return Valid document identifier, a corrupt-data error otherwise
     */
    public static DocId parse(final ItemKey key) {
        return DocDataKeys.parse(key);
    }
}
