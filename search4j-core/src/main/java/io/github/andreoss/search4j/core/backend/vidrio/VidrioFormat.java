/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk table format definitions of the vidrio backend.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import java.nio.charset.StandardCharsets;

/**
 * Format-wide constants of the vidrio table files.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class VidrioFormat {

    /**
     * Number of B-tree levels a cursor allows for.
     */
    public static final int CURSOR_LEVELS = 10;

    /**
     * Largest document number the format stores, as unsigned 64 bits.
     */
    public static final long MAX_DOCID = 0xFFFF_FFFF_FFFF_FFFFL;

    /**
     * Table file name extension, ASCII bytes without the dot.
     */
    private static final byte[] EXTENSION = {0x67, 0x6C, 0x61, 0x73, 0x73};

    /**
     * Utility class, no instances.
     */
    private VidrioFormat() {
    }

    /**
     * Table file name extension as bytes.
     *
     * @return Fresh copy of the ASCII extension, without the dot
     */
    public static byte[] extension() {
        return VidrioFormat.EXTENSION.clone();
    }

    /**
     * Table file name extension as text.
     *
     * @return ASCII extension without the dot
     */
    public static String extensionText() {
        return new String(VidrioFormat.EXTENSION, StandardCharsets.US_ASCII);
    }
}
