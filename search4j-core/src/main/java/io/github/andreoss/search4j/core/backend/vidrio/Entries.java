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

import io.github.andreoss.search4j.core.error.UnimplementedError;
import io.github.andreoss.search4j.core.ser.Zlib;
import java.nio.ByteBuffer;

/**
 * Entries of a tree being written: a tag is stored as one or more leaf
 * items of its key, numbered from 1, and replaces those of an entry with
 * the same key.
 *
 * @since 0.1.0
 */
final class Entries {

    /**
     * Path being changed.
     */
    private final Spine spine;

    /**
     * Item changes.
     */
    private final TreeEdit edit;

    /**
     * Item being added or sought.
     */
    private final Draft draft;

    /**
     * Smallest tag length compressed, 0 for none.
     */
    private final int compress;

    /**
     * Largest item size.
     */
    private final int maxsize;

    /**
     * Whether full compaction is on.
     */
    private boolean full;

    /**
     * Creates the entries of a path.
     *
     * @param spine Path, open at the root
     * @param edit Item changes
     * @param draft Item buffer of the block size
     * @param compress Smallest tag length compressed, 0 for none
     * @param maxsize Largest item size
     */
    Entries(
        final Spine spine, final TreeEdit edit, final Draft draft,
        final int compress, final int maxsize
    ) {
        this.spine = spine;
        this.edit = edit;
        this.draft = draft;
        this.compress = compress;
        this.maxsize = maxsize;
    }

    /**
     * Creates the entries of a path for a table.
     *
     * @param spine Path, open at the root
     * @param edit Item changes
     * @param state Root information of the table
     * @return Entries over the path
     */
    static Entries of(
        final Spine spine, final TreeEdit edit, final TableState state
    ) {
        final int size = state.root().size().bytes();
        return new Entries(
            spine, edit, new Draft(size), state.compress(),
            Math.min(
                (size - Page.START - LeafItem.BLOCK_CAPACITY * Page.ENTRY)
                    / LeafItem.BLOCK_CAPACITY,
                LeafItem.MAX_SIZE
            )
        );
    }

    /**
     * Sets full compaction: blocks are filled as much as possible.
     *
     * @param flag True to fill blocks
     */
    void compaction(final boolean flag) {
        if (flag) {
            this.edit.compaction();
        }
        this.full = flag;
    }

    /**
     * Adds an entry, replacing any entry with the same key.
     *
     * @param key Key bytes
     * @param tag Tag bytes
     * @param packed Whether the tag is already compressed
     * @return True if an entry was replaced
     */
    boolean put(final byte[] key, final byte[] tag, final boolean packed) {
        this.draft.form(key);
        byte[] data = tag;
        boolean deflated = packed;
        if (!packed && this.compress > 0 && tag.length > this.compress) {
            final byte[] small = Zlib.compress(tag);
            if (small.length < tag.length) {
                deflated = true;
                data = small;
            }
        }
        return this.store(data, deflated);
    }

    /**
     * Deletes an entry.
     *
     * @param key Key bytes, 1 to {@link ItemKey#MAX_LENGTH} long
     * @return True if the entry existed
     */
    boolean delete(final byte[] key) {
        this.draft.form(key);
        final int first = this.deleteItem();
        this.trim(1, first == 1);
        return first != 0;
    }

    private boolean store(final byte[] data, final boolean deflated) {
        final int head = this.draft.keyLength() + ItemKey.LENGTH_BYTES
            + LeafItem.SIZE_BYTES + TableItem.COUNT_BYTES;
        final int room = this.maxsize - head;
        boolean found = this.spine.find(this.draft);
        final int first = this.firstChunk(data.length, head, found);
        final int parts = (data.length - first + room - 1) / room + 1;
        if (parts > 0xFFFF) {
            throw new UnimplementedError(
                String.format(
                    "Tag of size %d is too large to store - increase the block size to raise this limit",
                    data.length
                )
            );
        }
        int done = 0;
        boolean replaced = false;
        int result = 0;
        for (int number = 1; number <= parts; ++number) {
            int len = room;
            int start = head;
            if (number == parts) {
                len = data.length - done;
            } else if (number == 1) {
                len = first;
            }
            if (number == 1) {
                start -= TableItem.COUNT_BYTES;
            }
            this.draft.chunk(start, ByteBuffer.wrap(data, done, len));
            this.draft.flags(deflated, number, parts);
            done += len;
            if (number > 1) {
                found = this.spine.find(this.draft);
            }
            result = this.addItem(found);
            replaced |= result != 0;
        }
        this.trim(parts, result == 1);
        return replaced;
    }

    private int firstChunk(final int size, final int head, final boolean found) {
        final int room = this.maxsize - head;
        int first = room + TableItem.COUNT_BYTES;
        if (size <= first) {
            first = size;
        } else if (!found) {
            first = this.fill(size, head, first);
        }
        return first;
    }

    private int fill(final int size, final int head, final int first) {
        final int room = this.maxsize - head;
        final int left = this.spine.page(0).totalFree()
            % (this.maxsize + Page.ENTRY) - Page.ENTRY - head;
        int len = first;
        if (left > 0 && (left >= (size - TableItem.COUNT_BYTES) % room
            || this.full && left >= this.draft.keyLength() + 34)) {
            len = left + TableItem.COUNT_BYTES;
        }
        return len;
    }

    private void trim(final int parts, final boolean extra) {
        if (extra) {
            int number = parts;
            int result = 1;
            while (result == 1) {
                number += 1;
                this.draft.component(number);
                result = this.deleteItem();
            }
        }
    }

    private int addItem(final boolean found) {
        this.spine.alter();
        int result = 0;
        if (found) {
            this.edit.restart();
            final Page page = this.spine.page(0);
            final int pos = this.spine.position(0);
            final byte[] item = this.draft.item();
            final int needed = item.length - page.itemSize(pos);
            result = Entries.status(page, pos);
            if (needed <= 0) {
                page.replace(pos, item);
                page.totalFree(page.totalFree() - needed);
            } else if (page.maxFree() >= item.length) {
                page.relocate(pos, item);
                page.totalFree(page.totalFree() - needed);
            } else {
                this.edit.deleteLeaf(false);
                this.edit.addLeaf(this.draft);
            }
        } else {
            this.edit.follow();
            this.spine.position(0, this.spine.position(0) + Page.ENTRY);
            this.edit.addLeaf(this.draft);
        }
        return result;
    }

    private int deleteItem() {
        this.edit.restart();
        int result = 0;
        if (this.spine.find(this.draft)) {
            result = Entries.status(this.spine.page(0), this.spine.position(0));
            this.spine.alter();
            this.edit.deleteLeaf(true);
        }
        return result;
    }

    private static int status(final Page page, final int pos) {
        int result = 1;
        if (page.last(pos)) {
            result = 2;
        }
        return result;
    }
}
