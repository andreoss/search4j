/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk table format definitions of the vidrio backend.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

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
        out.writeBytes(Varints.pack(this.root.size().bytes() >> 11));
        out.writeBytes(Varints.pack(this.compress));
        final byte[] list = this.free.bytes();
        out.writeBytes(Varints.pack(list.length));
        out.writeBytes(list);
        return out.toByteArray();
    }
}
