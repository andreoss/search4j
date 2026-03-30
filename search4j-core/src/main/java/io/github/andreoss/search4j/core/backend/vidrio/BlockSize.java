/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk table format definitions of the vidrio backend.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

/**
 * Size of a B-tree block: a power of two from 2048 to 65536 bytes.
 *
 * @param bytes Block size in bytes
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public record BlockSize(int bytes) {

    /**
     * Smallest block size in bytes.
     */
    public static final int MIN_BYTES = 2048;

    /**
     * Largest block size in bytes.
     */
    public static final int MAX_BYTES = 65_536;

    /**
     * Default block size in bytes.
     */
    public static final int DEFAULT_BYTES = 8192;

    /**
     * Smallest block size.
     */
    public static final BlockSize MIN = new BlockSize(BlockSize.MIN_BYTES);

    /**
     * Largest block size.
     */
    public static final BlockSize MAX = new BlockSize(BlockSize.MAX_BYTES);

    /**
     * Default block size.
     */
    public static final BlockSize DEFAULT =
        new BlockSize(BlockSize.DEFAULT_BYTES);

    /**
     * Creates a block size.
     *
     * @param bytes Power of two from 2048 to 65536
     */
    public BlockSize {
        if (!BlockSize.valid(bytes)) {
            throw new IllegalArgumentException(
                String.format("Bad block size: %d", bytes)
            );
        }
    }

    /**
     * Block size from a requested size, the default when it is invalid.
     *
     * @param bytes Requested size in bytes
     * @return Requested size, or {@link #DEFAULT}
     */
    public static BlockSize orDefault(final int bytes) {
        final BlockSize size;
        if (BlockSize.valid(bytes)) {
            size = new BlockSize(bytes);
        } else {
            size = BlockSize.DEFAULT;
        }
        return size;
    }

    /**
     * Block size from its shift over the minimum.
     *
     * @param shift Shift from 0 to 5
     * @return Minimum size shifted left by the shift
     */
    public static BlockSize fromShift(final int shift) {
        if (shift < 0 || shift > BlockSize.shiftOf(BlockSize.MAX_BYTES)) {
            throw new IllegalArgumentException(
                String.format("Bad block size shift: %d", shift)
            );
        }
        return new BlockSize(BlockSize.MIN_BYTES << shift);
    }

    /**
     * Shift of this size over the minimum.
     *
     * @return Shift from 0 to 5
     */
    public int shift() {
        return BlockSize.shiftOf(this.bytes);
    }

    private static boolean valid(final int bytes) {
        return bytes >= BlockSize.MIN_BYTES && bytes <= BlockSize.MAX_BYTES
            && Integer.bitCount(bytes) == 1;
    }

    private static int shiftOf(final int bytes) {
        return Integer.numberOfTrailingZeros(bytes)
            - Integer.numberOfTrailingZeros(BlockSize.MIN_BYTES);
    }
}
