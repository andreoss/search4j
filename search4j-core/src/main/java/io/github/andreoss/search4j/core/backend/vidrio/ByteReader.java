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

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import java.util.Arrays;

/**
 * Cursor over encoded bytes, decoding variable-length integers and
 * length-prefixed strings the way the reference unpacks them.
 *
 * <p>Running out of data or an integer wider than its field raises
 * {@link DatabaseCorruptError}.</p>
 *
 * @since 0.1.0
 */
final class ByteReader {

    /**
     * Bits carried by one encoded byte.
     */
    private static final int GROUP = 7;

    /**
     * Bytes read.
     */
    private final byte[] data;

    /**
     * Offset after the last byte.
     */
    private final int end;

    /**
     * Offset of the next byte.
     */
    private int pos;

    /**
     * Creates a reader over a range of the bytes.
     *
     * @param data Bytes, not copied
     * @param start Offset of the first byte
     * @param end Offset after the last byte
     */
    private ByteReader(final byte[] data, final int start, final int end) {
        this.data = data;
        this.pos = start;
        this.end = end;
    }

    /**
     * Creates a reader over all of the bytes.
     *
     * @param data Bytes, not copied
     * @return Reader at the first byte
     */
    static ByteReader over(final byte[] data) {
        return new ByteReader(data, 0, data.length);
    }

    /**
     * Creates a reader over a range of the bytes.
     *
     * @param data Bytes, not copied
     * @param start Offset of the first byte
     * @param end Offset after the last byte
     * @return Reader at the start
     */
    static ByteReader over(
        final byte[] data, final int start, final int end
    ) {
        return new ByteReader(data, start, end);
    }

    /**
     * Decodes an unsigned integer of up to 64 bits.
     *
     * @param what Field name for the error
     * @return Value, negative when above {@link Long#MAX_VALUE}
     */
    long wide(final String what) {
        return this.uint(what, Long.SIZE);
    }

    /**
     * Decodes an unsigned integer of up to 32 bits.
     *
     * @param what Field name for the error
     * @return Value from 0 to 4294967295
     */
    long narrow(final String what) {
        return this.uint(what, Integer.SIZE);
    }

    /**
     * Decodes a flag stored as the digit 0 or 1.
     *
     * @param what Field name for the error
     * @return True for the digit 1
     */
    boolean flag(final String what) {
        if (this.pos == this.end) {
            throw ByteReader.corrupt(what, "out of data");
        }
        final int digit = this.data[this.pos] - '0';
        if ((digit & ~1) != 0) {
            throw ByteReader.corrupt(what, "is not a flag");
        }
        this.pos += 1;
        return digit == 1;
    }

    /**
     * Decodes a length-prefixed string.
     *
     * @param what Field name for the error
     * @return Copy of the string bytes
     */
    byte[] string(final String what) {
        final long len = this.wide(what);
        if (len < 0L || len > this.end - this.pos) {
            throw ByteReader.corrupt(what, "out of data");
        }
        final byte[] result = Arrays.copyOfRange(
            this.data, this.pos, this.pos + (int) len
        );
        this.pos += (int) len;
        return result;
    }

    /**
     * Reads one byte.
     *
     * @param what Field name for the error
     * @return Byte value from 0 to 255
     */
    int octet(final String what) {
        if (this.pos == this.end) {
            throw ByteReader.corrupt(what, "out of data");
        }
        final int result = Byte.toUnsignedInt(this.data[this.pos]);
        this.pos += 1;
        return result;
    }

    /**
     * Reads a run of bytes.
     *
     * @param count Number of bytes
     * @param what Field name for the error
     * @return Copy of the bytes
     */
    byte[] bytes(final int count, final String what) {
        if (count > this.end - this.pos) {
            throw ByteReader.corrupt(what, "out of data");
        }
        final byte[] result =
            Arrays.copyOfRange(this.data, this.pos, this.pos + count);
        this.pos += count;
        return result;
    }

    /**
     * Tests whether every byte was read.
     *
     * @return True at the end
     */
    boolean done() {
        return this.pos == this.end;
    }

    /**
     * Offset of the next byte.
     *
     * @return Offset into the bytes read
     */
    int offset() {
        return this.pos;
    }

    /**
     * Reads every remaining byte.
     *
     * @return Copy of the bytes not read yet
     */
    byte[] rest() {
        final byte[] result = Arrays.copyOfRange(this.data, this.pos, this.end);
        this.pos = this.end;
        return result;
    }

    private long uint(final String what, final int bits) {
        int len = 0;
        while (true) {
            if (this.pos + len == this.end) {
                throw ByteReader.corrupt(what, "out of data");
            }
            final byte item = this.data[this.pos + len];
            ++len;
            if (item >= 0) {
                break;
            }
        }
        if (len * ByteReader.GROUP - ByteReader.GROUP + 1 > bits) {
            throw ByteReader.corrupt(what, "overflowed");
        }
        long result = 0L;
        for (int idx = len - 1; idx >= 0; --idx) {
            final long chunk = this.data[this.pos + idx] & 0x7FL;
            if (idx * ByteReader.GROUP + ByteReader.bitsOf(chunk) > bits) {
                throw ByteReader.corrupt(what, "overflowed");
            }
            result |= chunk << idx * ByteReader.GROUP;
        }
        this.pos += len;
        return result;
    }

    private static int bitsOf(final long chunk) {
        return Long.SIZE - Long.numberOfLeadingZeros(chunk);
    }

    private static DatabaseCorruptError corrupt(
        final String what, final String why
    ) {
        return new DatabaseCorruptError(String.format("%s %s", what, why));
    }
}
