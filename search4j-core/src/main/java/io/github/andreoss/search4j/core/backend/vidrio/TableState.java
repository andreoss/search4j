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
 * Root information of a committed table revision, kept by the version
 * record of the database.
 *
 * @param root Root of the tree at the revision
 * @param entries Number of entries
 * @param sequential Whether every item was added in key order
 * @param compress Smallest tag length compressed, 0 for no compression
 * @param free Free list at the revision
 * @since 0.1.0
 */
public record TableState(
    TableRoot root, EntryCount entries, boolean sequential, int compress,
    FreeListInfo free
) {

    /**
     * Shift between a block size and its stored form.
     */
    private static final int SIZE_SHIFT = 11;

    /**
     * Creates a state.
     *
     * @param root Root of the tree
     * @param entries Number of entries
     * @param sequential Whether items were added in key order
     * @param compress Non-negative compression threshold
     * @param free Free list
     */
    public TableState {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(free, "free");
        if (compress < 0) {
            throw new IllegalArgumentException(
                String.format("Bad compression threshold: %d", compress)
            );
        }
    }

    /**
     * The state of a new empty table, its root not on disk.
     *
     * @param size Block size
     * @param compress Compression threshold, 0 for none
     * @return State at revision 0
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static TableState initial(final BlockSize size, final int compress) {
        return new TableState(
            TableRoot.empty(size, new RevisionNumber(0L)), EntryCount.EMPTY,
            true, compress, FreeListInfo.EMPTY
        );
    }

    /**
     * Encodes the state as stored in the version record.
     *
     * @return Variable-length integers and the length-prefixed free list
     */
    public byte[] bytes() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(Varints.pack(this.root.block().value()));
        int flags = this.root.level() << 2;
        if (this.sequential) {
            flags |= 2;
        }
        if (this.root.fake()) {
            flags |= 1;
        }
        out.writeBytes(Varints.pack(flags));
        out.writeBytes(Varints.pack(this.entries.value()));
        out.writeBytes(
            Varints.pack(this.root.size().bytes() >> TableState.SIZE_SHIFT)
        );
        out.writeBytes(Varints.pack(this.compress));
        final byte[] list = this.free.bytes();
        out.writeBytes(Varints.pack(list.length));
        out.writeBytes(list);
        return out.toByteArray();
    }

    /**
     * Decodes the root information of a table from the version record.
     *
     * @param reader Bytes at the root information
     * @param revision Revision of the version record
     * @return State at the revision
     */
    static TableState parse(
        final ByteReader reader, final RevisionNumber revision
    ) {
        final long block = reader.narrow("Root block");
        final long flags = reader.narrow("Root flags");
        final long entries = reader.wide("Entry count");
        final long shift = reader.narrow("Block size");
        final long compress = reader.narrow("Compression threshold");
        final byte[] list = reader.string("Free list");
        final int level = (int) (flags >> 2);
        if (level >= VidrioFormat.CURSOR_LEVELS) {
            throw new DatabaseCorruptError("Impossibly deep tree");
        }
        final boolean fake = (flags & 1L) != 0L;
        if (fake && level > 0) {
            throw new DatabaseCorruptError("Fake root but level > 0");
        }
        if (shift > BlockSize.MAX_BYTES >> TableState.SIZE_SHIFT
            || Long.bitCount(shift) != 1) {
            throw new DatabaseCorruptError("Invalid block size");
        }
        if (entries < 0L) {
            throw new DatabaseCorruptError("Entry count overflowed");
        }
        if (compress > Integer.MAX_VALUE) {
            throw new DatabaseCorruptError("Compression threshold overflowed");
        }
        int threshold = (int) compress;
        if (threshold == 4) {
            threshold = TableType.COMPRESS_MIN;
        }
        return new TableState(
            new TableRoot(
                new BlockNumber(block), level,
                new BlockSize((int) shift << TableState.SIZE_SHIFT), revision,
                fake
            ),
            new EntryCount(entries), (flags & 2L) != 0L, threshold,
            FreeListInfo.parse(list)
        );
    }
}
