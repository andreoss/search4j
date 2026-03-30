/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk table format definitions of the vidrio backend.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.ser.SortableUints;
import io.github.andreoss.search4j.core.types.DocId;
import java.nio.ByteBuffer;

/**
 * Keys of the document data table: the sort-preserving document number.
 *
 * <p>Empty document data is not stored.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class DocDataKeys {

    /**
     * Utility class, no instances.
     */
    private DocDataKeys() {
    }

    /**
     * Key of a document's data.
     *
     * @param doc Document identifier
     * @return Key
     */
    public static ItemKey of(final DocId doc) {
        return ItemKey.of(SortableUints.pack(doc.value()));
    }

    /**
     * Document identifier of a document data key.
     *
     * @param key Key from {@link #of(DocId)}
     * @return Valid document identifier
     */
    public static DocId parse(final ItemKey key) {
        final byte[] data = key.data();
        if (data.length == 0 || SortableUints.width(data[0]) != data.length) {
            throw new DatabaseCorruptError(
                String.format("Bad document data key: %s", key)
            );
        }
        final long number = SortableUints.unpack(ByteBuffer.wrap(data));
        if (number < 1L || number > Integer.MAX_VALUE) {
            throw new DatabaseCorruptError(
                String.format("Document data key out of range: %s", key)
            );
        }
        return DocId.of((int) number);
    }
}
