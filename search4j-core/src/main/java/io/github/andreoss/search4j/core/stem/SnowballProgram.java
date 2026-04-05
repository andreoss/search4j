/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Snowball stemming runtime and language registry.
 */
package io.github.andreoss.search4j.core.stem;

import io.github.andreoss.search4j.core.error.EngineInternalError;

/**
 * Base of a Snowball algorithm: the buffer, the slice marks and the
 * matching primitives the generated algorithms are written against.
 *
 * <p>A subclass implements {@link #stem()}, which rewrites the current
 * buffer in place. The primitives follow the Snowball runtime: forward
 * matching starts at the cursor, backward matching ends at the cursor and
 * the limit is the lower bound in backward mode.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.TooManyMethods",
    "PMD.AvoidStringBufferField"
})
public abstract class SnowballProgram {

    /**
     * Word being stemmed.
     */
    private final StringBuilder current;

    /**
     * Cursor of the forward or backward scan.
     */
    private int cursor;

    /**
     * Upper bound of a forward scan, lower bound of a backward scan.
     */
    private int limit;

    /**
     * Start of the current slice.
     */
    private int bra;

    /**
     * End of the current slice.
     */
    private int ket;

    /**
     * Lower bound of a backward scan.
     */
    private int back;

    /**
     * Creates an empty program.
     */
    protected SnowballProgram() {
        this.current = new StringBuilder();
        this.cursor = 0;
        this.limit = 0;
        this.bra = 0;
        this.ket = 0;
        this.back = 0;
    }

    /**
     * Rewrites the current buffer in place.
     */
    public abstract void stem();

    protected final String current() {
        return this.current.toString();
    }

    protected final void current(final String text) {
        this.current.setLength(0);
        this.current.append(text);
        this.cursor = 0;
        this.limit = this.current.length();
        this.bra = 0;
        this.ket = 0;
        this.back = 0;
    }

    protected final void copyFrom(final SnowballProgram other) {
        this.current.setLength(0);
        this.current.append(other.current);
        this.cursor = other.cursor;
        this.limit = other.limit;
        this.bra = other.bra;
        this.ket = other.ket;
        this.back = other.back;
    }

    protected final int cursor() {
        return this.cursor;
    }

    protected final void cursor(final int value) {
        this.cursor = value;
    }

    protected final int limit() {
        return this.limit;
    }

    protected final void limit(final int value) {
        this.limit = value;
    }

    protected final int bra() {
        return this.bra;
    }

    protected final void bra(final int value) {
        this.bra = value;
    }

    protected final int ket() {
        return this.ket;
    }

    protected final void ket(final int value) {
        this.ket = value;
    }

    protected final int back() {
        return this.back;
    }

    protected final void back(final int value) {
        this.back = value;
    }

    protected final boolean nextChar() {
        final boolean moved = this.cursor < this.limit;
        if (moved) {
            this.cursor = this.cursor + 1;
        }
        return moved;
    }

    protected final boolean prevChar() {
        final boolean moved = this.cursor > this.back;
        if (moved) {
            this.cursor = this.cursor - 1;
        }
        return moved;
    }

    protected final boolean inGrouping(
        final int[] groups, final int min, final int max
    ) {
        boolean found = false;
        if (this.cursor < this.limit) {
            found = SnowballProgram.grouped(
                this.current.charAt(this.cursor), groups, min, max
            );
        }
        if (found) {
            this.cursor = this.cursor + 1;
        }
        return found;
    }

    protected final boolean inGroupingB(
        final int[] groups, final int min, final int max
    ) {
        boolean found = false;
        if (this.cursor > this.back) {
            found = SnowballProgram.grouped(
                this.current.charAt(this.cursor - 1), groups, min, max
            );
        }
        if (found) {
            this.cursor = this.cursor - 1;
        }
        return found;
    }

    protected final boolean outGrouping(
        final int[] groups, final int min, final int max
    ) {
        boolean found = false;
        if (this.cursor < this.limit) {
            found = !SnowballProgram.grouped(
                this.current.charAt(this.cursor), groups, min, max
            );
        }
        if (found) {
            this.cursor = this.cursor + 1;
        }
        return found;
    }

    protected final boolean outGroupingB(
        final int[] groups, final int min, final int max
    ) {
        boolean found = false;
        if (this.cursor > this.back) {
            found = !SnowballProgram.grouped(
                this.current.charAt(this.cursor - 1), groups, min, max
            );
        }
        if (found) {
            this.cursor = this.cursor - 1;
        }
        return found;
    }

    protected final boolean inRange(final int min, final int max) {
        boolean found = false;
        if (this.cursor < this.limit) {
            final int chr = this.current.charAt(this.cursor);
            found = chr >= min && chr <= max;
        }
        if (found) {
            this.cursor = this.cursor + 1;
        }
        return found;
    }

    protected final boolean inRangeB(final int min, final int max) {
        boolean found = false;
        if (this.cursor > this.back) {
            final int chr = this.current.charAt(this.cursor - 1);
            found = chr >= min && chr <= max;
        }
        if (found) {
            this.cursor = this.cursor - 1;
        }
        return found;
    }

    protected final boolean outRange(final int min, final int max) {
        boolean found = false;
        if (this.cursor < this.limit) {
            final int chr = this.current.charAt(this.cursor);
            found = chr < min || chr > max;
        }
        if (found) {
            this.cursor = this.cursor + 1;
        }
        return found;
    }

    protected final boolean outRangeB(final int min, final int max) {
        boolean found = false;
        if (this.cursor > this.back) {
            final int chr = this.current.charAt(this.cursor - 1);
            found = chr < min || chr > max;
        }
        if (found) {
            this.cursor = this.cursor - 1;
        }
        return found;
    }

    protected final boolean eqS(final String text) {
        final int size = text.length();
        boolean equal = this.limit - this.cursor >= size;
        for (int idx = 0; equal && idx < size; idx = idx + 1) {
            equal = this.current.charAt(this.cursor + idx) == text.charAt(idx);
        }
        if (equal) {
            this.cursor = this.cursor + size;
        }
        return equal;
    }

    protected final boolean eqSB(final String text) {
        final int size = text.length();
        boolean equal = this.cursor - this.back >= size;
        for (int idx = 0; equal && idx < size; idx = idx + 1) {
            equal = this.current.charAt(this.cursor - size + idx)
                == text.charAt(idx);
        }
        if (equal) {
            this.cursor = this.cursor - size;
        }
        return equal;
    }

    protected final boolean eqV(final CharSequence text) {
        final int size = text.length();
        boolean equal = this.limit - this.cursor >= size;
        for (int idx = 0; equal && idx < size; idx = idx + 1) {
            equal = this.current.charAt(this.cursor + idx) == text.charAt(idx);
        }
        if (equal) {
            this.cursor = this.cursor + size;
        }
        return equal;
    }

    protected final boolean eqVB(final CharSequence text) {
        final int size = text.length();
        boolean equal = this.cursor - this.back >= size;
        for (int idx = 0; equal && idx < size; idx = idx + 1) {
            equal = this.current.charAt(this.cursor - size + idx)
                == text.charAt(idx);
        }
        if (equal) {
            this.cursor = this.cursor - size;
        }
        return equal;
    }

    protected final int findAmong(final Among... among) {
        int result = 0;
        int size = 0;
        for (final Among entry : among) {
            final String text = entry.text();
            final int length = text.length();
            if (length > size && this.limit - this.cursor >= length
                && SnowballProgram.startsWith(this.current, this.cursor, text)) {
                result = entry.result();
                size = length;
            }
        }
        if (result != 0) {
            this.cursor = this.cursor + size;
        }
        return result;
    }

    protected final int findAmongB(final Among... among) {
        int result = 0;
        int size = 0;
        for (final Among entry : among) {
            final String text = entry.text();
            final int length = text.length();
            if (length > size && this.cursor - this.back >= length
                && SnowballProgram.endsWith(this.current, this.cursor, text)) {
                result = entry.result();
                size = length;
            }
        }
        if (result != 0) {
            this.cursor = this.cursor - size;
            this.bra = this.cursor;
        }
        return result;
    }

    protected final int replaceS(
        final int cbra, final int cket, final String text
    ) {
        final int adjustment = text.length() - (cket - cbra);
        if (adjustment != 0) {
            this.limit = this.limit + adjustment;
            if (this.cursor >= cket) {
                this.cursor = this.cursor + adjustment;
            } else if (this.cursor >= cbra) {
                this.cursor = cbra;
            }
        }
        this.current.replace(cbra, cket, text);
        return adjustment;
    }

    protected final void sliceFrom(final String text) {
        this.sliceCheck();
        this.replaceS(this.bra, this.ket, text);
    }

    protected final void sliceDel() {
        this.sliceFrom("");
    }

    protected final void insert(
        final int cbra, final int cket, final String text
    ) {
        this.replaceS(cbra, cket, text);
    }

    protected final void sliceCheck() {
        if (this.bra < 0 || this.bra > this.ket || this.ket > this.limit) {
            throw new EngineInternalError(
                String.format(
                    "Invalid slice %d..%d of %d", this.bra, this.ket,
                    this.limit
                )
            );
        }
    }

    private static boolean grouped(
        final char chr, final int[] groups, final int min, final int max
    ) {
        boolean found = chr >= min && chr <= max;
        if (found) {
            final int index = chr - min;
            found = (groups[index >> 5] & 1 << (index & 0x1F)) != 0;
        }
        return found;
    }

    private static boolean startsWith(
        final CharSequence text, final int from, final String part
    ) {
        boolean same = true;
        for (int idx = 0; same && idx < part.length(); idx = idx + 1) {
            same = text.charAt(from + idx) == part.charAt(idx);
        }
        return same;
    }

    private static boolean endsWith(
        final CharSequence text, final int end, final String part
    ) {
        boolean same = true;
        final int from = end - part.length();
        for (int idx = 0; same && idx < part.length(); idx = idx + 1) {
            same = text.charAt(from + idx) == part.charAt(idx);
        }
        return same;
    }
}
