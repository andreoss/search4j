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
     * Number of magic bytes starting a version file.
     */
    public static final int MAGIC_BYTES = 14;

    /**
     * Format version stored after the magic bytes: the date of the last
     * format change as two big-endian bytes, 2016-03-14.
     */
    public static final int FORMAT_VERSION =
        VidrioFormat.dateVersion(2016, 3, 14);

    /**
     * Table file name extension, ASCII bytes without the dot.
     */
    private static final byte[] EXTENSION = {0x67, 0x6C, 0x61, 0x73, 0x73};

    /**
     * Name of the version file inside the database directory, ASCII bytes.
     */
    private static final byte[] VERSION_FILE = {
        0x69, 0x61, 0x6D, 0x67, 0x6C, 0x61, 0x73, 0x73,
    };

    /**
     * Magic bytes starting a version file.
     */
    private static final byte[] MAGIC = {
        0x0F, 0x0D, 0x58, 0x61, 0x70, 0x69, 0x61, 0x6E, 0x20, 0x47, 0x6C,
        0x61, 0x73, 0x73,
    };

    /**
     * Year the version date encoding counts from.
     */
    private static final int EPOCH = 2014;

    /**
     * Shift of the year in a version date.
     */
    private static final int YEAR_SHIFT = 9;

    /**
     * Shift of the month in a version date.
     */
    private static final int MONTH_SHIFT = 5;

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

    /**
     * Name of the version file as text.
     *
     * @return ASCII file name
     */
    public static String versionFileName() {
        return new String(VidrioFormat.VERSION_FILE, StandardCharsets.US_ASCII);
    }

    /**
     * Magic bytes starting a version file.
     *
     * @return Fresh copy of the {@link #MAGIC_BYTES} bytes
     */
    public static byte[] versionMagic() {
        return VidrioFormat.MAGIC.clone();
    }

    /**
     * Encodes a date as a format version number.
     *
     * @param year Year from 2014 to 2141
     * @param month Month from 1 to 12
     * @param day Day from 1 to 31
     * @return Version number fitting two bytes
     */
    public static int dateVersion(
        final int year, final int month, final int day
    ) {
        return (year - VidrioFormat.EPOCH) << VidrioFormat.YEAR_SHIFT
            | month << VidrioFormat.MONTH_SHIFT | day;
    }

    /**
     * Decodes a format version number as a decimal date.
     *
     * @param version Version number from {@link #dateVersion(int, int, int)}
     * @return Date as year, month and day digits, like 20160314
     */
    public static int versionDate(final int version) {
        return ((version >> VidrioFormat.YEAR_SHIFT) + VidrioFormat.EPOCH)
            * 10_000
            + (version >> VidrioFormat.MONTH_SHIFT & 0x0F) * 100
            + (version & 0x1F);
    }
}
