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
import java.nio.charset.StandardCharsets;

/**
 * Keys of the posting list table.
 *
 * <p>The first chunk of a term is keyed by the sort-preserving term alone;
 * every later chunk by the term with its terminator and the sort-preserving
 * number of its first document. The document lengths are the posting list
 * of the empty term, whose keys start with a two-byte prefix instead.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class PostListKeys {

    /**
     * Prefix of the keys of the empty term.
     */
    private static final byte[] LENGTHS = {0x00, (byte) 0xE0};

    /**
     * Utility class, no instances.
     */
    private PostListKeys() {
    }

    /**
     * Bytes of a term as stored in keys.
     *
     * @param term Term text
     * @return UTF-8 bytes
     */
    public static byte[] bytes(final String term) {
        return term.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Key of the first chunk of a term.
     *
     * @param term Term bytes, empty for the document lengths
     * @return Key bytes
     */
    public static byte[] first(final byte[] term) {
        final byte[] key;
        if (term.length == 0) {
            key = PostListKeys.LENGTHS.clone();
        } else {
            key = SortableStrings.pack(term, true);
        }
        return key;
    }

    /**
     * Key of a later chunk of a term.
     *
     * @param term Term bytes, empty for the document lengths
     * @param start First document of the chunk
     * @return Key bytes
     */
    public static byte[] chunk(final byte[] term, final DocId start) {
        return PostListKeys.keyOf(term, start.value());
    }

    /**
     * Key after every chunk key of a term.
     *
     * @param term Term bytes, empty for the document lengths
     * @return Key bytes of the largest document number the format allows
     */
    static byte[] last(final byte[] term) {
        return PostListKeys.keyOf(term, VidrioFormat.MAX_DOCID);
    }

    /**
     * Length of the prefix of the empty term at the start of a key.
     *
     * @param key Key bytes
     * @return Prefix length, 0 when the key is of another term
     */
    static int lengthsPrefix(final byte[] key) {
        int length = 0;
        if (key.length >= PostListKeys.LENGTHS.length
            && key[0] == PostListKeys.LENGTHS[0]
            && key[1] == PostListKeys.LENGTHS[1]) {
            length = PostListKeys.LENGTHS.length;
        }
        return length;
    }

    private static byte[] keyOf(final byte[] term, final long start) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (term.length == 0) {
            out.writeBytes(PostListKeys.LENGTHS);
        } else {
            out.writeBytes(SortableStrings.pack(term, false));
        }
        out.writeBytes(SortableUints.pack(start));
        return out.toByteArray();
    }
}
