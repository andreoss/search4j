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

import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Keys of the user metadata in the posting list table: a two-byte prefix
 * and the key text.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class MetadataKeys {

    /**
     * Prefix of the metadata keys.
     */
    private static final byte[] PREFIX = {0x00, (byte) 0xC0};

    /**
     * Utility class, no instances.
     */
    private MetadataKeys() {
    }

    /**
     * Key of a metadata item.
     *
     * @param key Key text, not empty
     * @return Key bytes
     */
    public static byte[] of(final String key) {
        if (key.isEmpty()) {
            throw new InvalidArgumentError("Empty metadata key");
        }
        return MetadataKeys.prefixed(key);
    }

    /**
     * Key bytes every metadata key with a prefix starts with.
     *
     * @param prefix Leading text, empty for every metadata key
     * @return Key bytes
     */
    public static byte[] prefixed(final String prefix) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(MetadataKeys.PREFIX);
        out.writeBytes(prefix.getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /**
     * Tests whether a key starts with prefix bytes.
     *
     * @param key Key of a posting list entry
     * @param prefix Key bytes from {@link #prefixed(String)}
     * @return True when the key starts with the bytes
     */
    public static boolean matches(final ItemKey key, final byte[] prefix) {
        final byte[] data = key.data();
        return data.length >= prefix.length
            && Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
    }

    /**
     * Key text of a metadata key.
     *
     * @param key Key of a metadata item
     * @return Text after the prefix
     */
    public static String nameOf(final ItemKey key) {
        final byte[] data = key.data();
        return new String(
            data, MetadataKeys.PREFIX.length,
            data.length - MetadataKeys.PREFIX.length, StandardCharsets.UTF_8
        );
    }
}
