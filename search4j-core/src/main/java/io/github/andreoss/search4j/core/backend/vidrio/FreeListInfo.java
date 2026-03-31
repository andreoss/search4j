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
import io.github.andreoss.search4j.core.ser.Varints;
import java.io.ByteArrayOutputStream;
import java.util.Objects;

/**
 * Committed state of a table's free list.
 *
 * <p>Blocks between the head and the tail are free for the next
 * revision; blocks from {@code unused} on were never used.</p>
 *
 * @param revision Revision the state was committed at
 * @param unused First block number never used
 * @param head Next free entry to hand out
 * @param tail End of the committed entries
 * @since 0.1.0
 */
public record FreeListInfo(
    RevisionNumber revision, BlockNumber unused, FreeListMark head,
    FreeListMark tail
) {

    /**
     * The free list of a table never committed.
     */
    public static final FreeListInfo EMPTY = new FreeListInfo(
        new RevisionNumber(0L), new BlockNumber(0L), FreeListMark.START,
        FreeListMark.START
    );

    /**
     * Creates a state.
     *
     * @param revision Revision committed at
     * @param unused First block number never used
     * @param head Next free entry
     * @param tail End of the committed entries
     */
    public FreeListInfo {
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(unused, "unused");
        Objects.requireNonNull(head, "head");
        Objects.requireNonNull(tail, "tail");
    }

    /**
     * Decodes the state stored with the table's root information.
     *
     * @param bytes Bytes from {@link #bytes()}
     * @return State, {@link #EMPTY} for no bytes
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static FreeListInfo parse(final byte[] bytes) {
        final FreeListInfo info;
        if (bytes.length == 0) {
            info = FreeListInfo.EMPTY;
        } else {
            final ByteReader reader = ByteReader.over(bytes);
            info = new FreeListInfo(
                new RevisionNumber(reader.narrow("Free list revision")),
                new BlockNumber(reader.narrow("Free list unused block")),
                FreeListInfo.unpacked(reader), FreeListInfo.unpacked(reader)
            );
            if (!reader.done()) {
                throw new DatabaseCorruptError("Bad free list metadata");
            }
        }
        return info;
    }

    /**
     * Encodes the state as stored with the table's root information.
     *
     * <p>A free list never committed encodes as no bytes.</p>
     *
     * @return Six variable-length integers, or no bytes
     */
    public byte[] bytes() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!FreeListInfo.EMPTY.equals(this)) {
            out.writeBytes(Varints.pack(this.revision.value()));
            out.writeBytes(Varints.pack(this.unused.value()));
            FreeListInfo.packed(out, this.head);
            FreeListInfo.packed(out, this.tail);
        }
        return out.toByteArray();
    }

    private static FreeListMark unpacked(final ByteReader reader) {
        final long block = reader.narrow("Free list block");
        final long offset = reader.narrow("Free list offset");
        if (offset > Integer.MAX_VALUE / Integer.BYTES) {
            throw new DatabaseCorruptError("Free list offset overflowed");
        }
        return new FreeListMark(
            new BlockNumber(block), (int) offset * Integer.BYTES
        );
    }

    private static void packed(
        final ByteArrayOutputStream out, final FreeListMark mark
    ) {
        out.writeBytes(Varints.pack(mark.block().value()));
        out.writeBytes(Varints.pack(mark.offset() / Integer.BYTES));
    }
}
