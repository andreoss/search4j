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
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Free list of a table being written: a chain of free-list blocks holding
 * the numbers of blocks free for reuse.
 *
 * <p>Blocks freed by a revision are appended after the committed end and
 * are handed out only from the next revision on; while the chain is empty
 * blocks come from the end of the file.</p>
 *
 * @since 0.1.0
 */
final class FreeBlocks {

    /**
     * Offset of the first entry of a free-list block.
     */
    private static final int BASE = 8;

    /**
     * Entry value of no block.
     */
    private static final long NONE = 0xFFFF_FFFFL;

    /**
     * Store of the blocks.
     */
    private final BlockStore store;

    /**
     * Block size.
     */
    private final int size;

    /**
     * Revision committed last.
     */
    private long revision;

    /**
     * First block number never used.
     */
    private long unused;

    /**
     * Block of the next entry to hand out.
     */
    private long headblk;

    /**
     * Offset of the next entry to hand out.
     */
    private int headpos;

    /**
     * Block of the committed end.
     */
    private long endblk;

    /**
     * Offset of the committed end.
     */
    private int endpos;

    /**
     * Block being appended to.
     */
    private long tailblk;

    /**
     * Offset of the next entry appended, 0 before the first block.
     */
    private int tailpos;

    /**
     * View of the head block.
     */
    private final ByteBuffer head;

    /**
     * Bytes of the block being appended to.
     */
    private final byte[] tailbytes;

    /**
     * View of the block being appended to.
     */
    private final ByteBuffer tail;

    /**
     * Whether the head block was read.
     */
    private boolean headread;

    /**
     * Whether the block appended to was read or started.
     */
    private boolean tailread;

    /**
     * Chain block consumed while an entry was being appended, freed once
     * the entry is in place; {@link #NONE} when none.
     */
    private long held;

    /**
     * Creates a free list over its buffers.
     *
     * @param store Store of the blocks
     * @param size Block size
     * @param head View of a buffer of the block size for the head block
     * @param tail Buffer of the block size for the block appended to
     */
    private FreeBlocks(
        final BlockStore store, final int size, final ByteBuffer head,
        final byte[] tail
    ) {
        this(store, size, head, tail, ByteBuffer.wrap(tail));
    }

    /**
     * Creates a free list over its buffers and their views.
     *
     * @param store Store of the blocks
     * @param size Block size
     * @param head View of the head block
     * @param tailbytes Bytes of the block appended to
     * @param tail View of the same bytes
     */
    private FreeBlocks(
        final BlockStore store, final int size, final ByteBuffer head,
        final byte[] tailbytes, final ByteBuffer tail
    ) {
        this.store = store;
        this.size = size;
        this.head = head;
        this.tailbytes = tailbytes;
        this.tail = tail;
        this.held = FreeBlocks.NONE;
    }

    /**
     * Creates a free list at a committed state.
     *
     * @param store Store of the blocks
     * @param info Committed state
     * @return Free list at the state
     */
    static FreeBlocks of(final BlockStore store, final FreeListInfo info) {
        final int size = store.size().bytes();
        final FreeBlocks free = new FreeBlocks(
            store, size, ByteBuffer.allocate(size), new byte[size]
        );
        free.restore(info);
        return free;
    }

    /**
     * Returns to a committed state, dropping blocks read.
     *
     * @param info Committed state
     */
    void restore(final FreeListInfo info) {
        this.revision = info.revision().value();
        this.unused = info.unused().value();
        this.headblk = info.head().block().value();
        this.headpos = info.head().offset();
        this.tailblk = info.tail().block().value();
        this.tailpos = info.tail().offset();
        this.endblk = this.tailblk;
        this.endpos = this.tailpos;
        this.headread = false;
        this.tailread = false;
    }

    /**
     * State to store with the table's root information.
     *
     * @return Committed state
     */
    FreeListInfo info() {
        return new FreeListInfo(
            new RevisionNumber(this.revision), new BlockNumber(this.unused),
            new FreeListMark(new BlockNumber(this.headblk), this.headpos),
            new FreeListMark(new BlockNumber(this.tailblk), this.tailpos)
        );
    }

    /**
     * First block number never used.
     *
     * @return Block number
     */
    long unused() {
        return this.unused;
    }

    /**
     * Hands out a free block.
     *
     * @return Block number
     */
    long take() {
        return this.next(false);
    }

    /**
     * Adds a block to the free list, free from the next revision on.
     *
     * @param block Block number
     */
    void mark(final long block) {
        if (!this.tailread) {
            this.tailread = true;
            if (this.tailpos != 0) {
                this.tail.put(0, this.read(this.tailblk));
            }
        }
        long consumed = FreeBlocks.NONE;
        if (this.tailpos == 0 || this.tailpos == this.size - Integer.BYTES) {
            this.chain(this.next(true));
            consumed = this.held;
            this.held = FreeBlocks.NONE;
        }
        this.tail.putInt(this.tailpos, (int) block);
        this.tailpos += Integer.BYTES;
        if (consumed != FreeBlocks.NONE) {
            this.mark(consumed);
        }
    }

    /**
     * Commits the entries appended: the tail block is written and the
     * committed end moves to it.
     *
     * @param number Revision committed
     */
    void commit(final long number) {
        this.revision = number;
        if (this.tailread && this.tailpos != 0) {
            Arrays.fill(
                this.tailbytes, this.tailpos, this.size - Integer.BYTES,
                (byte) 0xFF
            );
            this.write(this.tailblk, this.revision);
            this.endblk = this.tailblk;
            this.endpos = this.tailpos;
        }
    }

    private void chain(final long fresh) {
        if (this.tailpos == 0) {
            if (this.headpos == 0) {
                this.headblk = fresh;
                this.headpos = FreeBlocks.BASE;
                this.endblk = fresh;
                this.endpos = FreeBlocks.BASE;
            }
        } else {
            this.tail.putInt(this.tailpos, (int) fresh);
            this.write(this.tailblk, this.revision + 1L);
        }
        this.tailblk = fresh;
        this.tailpos = FreeBlocks.BASE;
        this.tail.putInt(this.size - Integer.BYTES, (int) FreeBlocks.NONE);
    }

    private long next(final boolean defer) {
        final long block;
        if (this.headblk == this.endblk && this.headpos == this.endpos) {
            block = this.unused;
            this.unused += 1L;
        } else {
            this.load();
            block = this.entry(defer);
        }
        return block;
    }

    private void load() {
        if (!this.headread) {
            if (this.headblk == FreeBlocks.NONE) {
                throw new DatabaseCorruptError("Freelist pointer invalid");
            }
            this.head.put(0, this.read(this.headblk));
            this.headread = true;
        }
    }

    private long entry(final boolean defer) {
        final long block;
        if (this.headpos == this.size - Integer.BYTES) {
            block = this.hop(defer);
        } else {
            block = Integer.toUnsignedLong(this.head.getInt(this.headpos));
            if (block == FreeBlocks.NONE) {
                throw new DatabaseCorruptError(
                    String.format(
                        "Ran off end of freelist (%d, %d)",
                        this.headblk, this.headpos
                    )
                );
            }
            this.headpos += Integer.BYTES;
        }
        return block;
    }

    private long hop(final boolean defer) {
        final long old = this.headblk;
        this.headblk = Integer.toUnsignedLong(this.head.getInt(this.headpos));
        if (this.headblk == FreeBlocks.NONE) {
            throw new DatabaseCorruptError("Freelist next pointer invalid");
        }
        this.headpos = FreeBlocks.BASE;
        this.head.put(0, this.read(this.headblk));
        if (defer) {
            this.held = old;
        } else {
            this.mark(old);
        }
        return this.next(false);
    }

    private byte[] read(final long block) {
        final Block read = this.store.read(new BlockNumber(block));
        if (read.level() != BlockHeader.LEVEL_FREELIST) {
            throw new DatabaseCorruptError("Freelist corrupt");
        }
        return read.copy();
    }

    private void write(final long block, final long rev) {
        this.tail.putInt(0, (int) rev);
        this.tail.putInt(Integer.BYTES, 0);
        this.tail.put(Integer.BYTES, (byte) BlockHeader.LEVEL_FREELIST);
        this.store.write(new BlockNumber(block), this.tailbytes);
        if (this.headread && block == this.headblk) {
            this.head.put(0, this.tailbytes);
        }
    }
}
