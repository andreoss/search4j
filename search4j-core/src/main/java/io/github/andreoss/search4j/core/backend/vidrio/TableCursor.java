/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk table format definitions of the vidrio backend.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.InvalidOperationError;

/**
 * Cursor over the entries of a table in key order.
 *
 * <p>A new cursor is unpositioned. An entry is a key with its tag; a tag
 * longer than one item is stored as components in consecutive items and
 * is reassembled, and inflated when compressed, on {@link #tag()}.</p>
 *
 * @since 0.1.0
 */
public final class TableCursor {

    /**
     * Message of an operation that needs an entry.
     */
    private static final String NO_ENTRY = "Cursor is not on an entry";

    /**
     * Path to the current leaf item.
     */
    private final TreePath tree;

    /**
     * Key of the current entry.
     */
    private ItemKey current;

    /**
     * Tag of the current entry once read.
     */
    private byte[] value;

    /**
     * How much of the current entry's tag has been read.
     */
    private TagStatus status;

    /**
     * Whether the leaf position is on an item.
     */
    private boolean positioned;

    /**
     * Whether the cursor has moved past the last entry.
     */
    private boolean after;

    /**
     * Whether the cursor has been positioned at least once.
     */
    private boolean sought;

    /**
     * Creates an unpositioned cursor.
     *
     * @param blocks Source of the blocks
     * @param top Root block, at the top level of the tree
     */
    public TableCursor(final Blocks blocks, final Block top) {
        this(new TreePath(blocks, top));
    }

    /**
     * Creates an unpositioned cursor over a path.
     *
     * @param tree Path into the tree
     */
    private TableCursor(final TreePath tree) {
        this.tree = tree;
        this.current = ItemKey.EMPTY;
        this.value = new byte[0];
        this.status = TagStatus.UNREAD;
        this.positioned = false;
        this.after = false;
        this.sought = false;
    }

    /**
     * Positions the cursor on the last entry at or before a key.
     *
     * <p>A key longer than {@link ItemKey#MAX_LENGTH} is never found; the
     * cursor goes to its truncated form.</p>
     *
     * @param key Key bytes
     * @return True if the key itself was found
     */
    public boolean seek(final byte[] key) {
        this.start();
        final boolean found = this.tree.find(key);
        if (found) {
            this.status = TagStatus.UNREAD;
        } else {
            this.settle();
        }
        this.current = this.tree.key();
        return found;
    }

    /**
     * Positions the cursor on the last entry before a key.
     *
     * <p>Without such an entry the cursor is left unpositioned.</p>
     *
     * @param key Key bytes
     */
    public void seekBefore(final byte[] key) {
        if (this.seek(key)) {
            if (this.tree.prev()) {
                this.status = TagStatus.ON_LAST;
                this.current = this.tree.key();
            } else {
                this.positioned = false;
            }
        }
    }

    /**
     * Positions the cursor on the first entry at or after a key.
     *
     * @param key Key bytes
     * @return True if the key itself was found
     */
    public boolean seekAtLeast(final byte[] key) {
        this.start();
        final boolean found = this.tree.find(key);
        if (found || this.tree.next()) {
            this.current = this.tree.key();
        } else {
            this.after = true;
            this.positioned = false;
        }
        this.status = TagStatus.UNREAD;
        return found;
    }

    /**
     * Positions the cursor on the empty key that starts every table.
     *
     * <p>{@link #next()} then moves to the first entry.</p>
     */
    public void rewind() {
        this.seekAtLeast(new byte[0]);
    }

    /**
     * Positions the cursor exactly on a key and reads its tag.
     *
     * @param key Key bytes
     * @return True if found; otherwise the cursor is unpositioned
     */
    public boolean exact(final byte[] key) {
        this.after = false;
        this.positioned = false;
        this.sought = true;
        this.status = TagStatus.UNREAD;
        final boolean found = this.tree.find(key);
        if (found) {
            this.current = this.tree.key();
            this.load();
        }
        return found;
    }

    /**
     * Moves to the next entry.
     *
     * @return False when unpositioned or past the last entry
     */
    public boolean next() {
        boolean moved = false;
        if (this.sought && !this.after) {
            if (this.status != TagStatus.READ) {
                this.positioned = this.advance();
            }
            if (this.positioned) {
                this.current = this.tree.key();
                this.status = TagStatus.UNREAD;
                moved = true;
            } else {
                this.after = true;
            }
        }
        return moved;
    }

    /**
     * Tests whether the cursor has moved past the last entry.
     *
     * @return True after the end
     */
    public boolean afterEnd() {
        return this.after;
    }

    /**
     * Key of the current entry.
     *
     * @return Key
     */
    public ItemKey key() {
        this.requireEntry();
        return this.current;
    }

    /**
     * Tag of the current entry, reassembled and inflated.
     *
     * @return Fresh copy of the tag bytes
     */
    public byte[] tag() {
        this.requireEntry();
        if (this.status == TagStatus.ON_LAST) {
            this.backUp();
        }
        if (this.status == TagStatus.UNREAD) {
            if (!this.positioned) {
                throw new InvalidOperationError(TableCursor.NO_ENTRY);
            }
            this.load();
        }
        return this.value.clone();
    }

    private void settle() {
        this.status = TagStatus.ON_LAST;
        if (this.tree.before()) {
            this.tree.clamp();
            if (!this.tree.prev()) {
                this.status = TagStatus.UNREAD;
            }
        }
    }

    private void backUp() {
        while (!this.tree.first()) {
            if (!this.tree.prev()) {
                this.positioned = false;
                throw new DatabaseCorruptError(
                    "No entry found at or before the key"
                );
            }
        }
        this.status = TagStatus.UNREAD;
    }

    private void requireEntry() {
        if (!this.sought || this.after) {
            throw new InvalidOperationError(TableCursor.NO_ENTRY);
        }
    }

    private void start() {
        this.after = false;
        this.positioned = true;
        this.sought = true;
    }

    private boolean advance() {
        boolean more = this.tree.next();
        while (more && this.status != TagStatus.ON_LAST
            && !this.tree.first()) {
            more = this.tree.next();
        }
        return more;
    }

    private void load() {
        this.value = this.tree.tag();
        this.status = TagStatus.READ;
        this.positioned = this.tree.next();
    }

    /**
     * How much of the current entry's tag has been read.
     *
     * @since 0.1.0
     */
    private enum TagStatus {

        /**
         * Not read, the cursor on the first component.
         */
        UNREAD,

        /**
         * Not read, the cursor on the last component.
         */
        ON_LAST,

        /**
         * Read, the cursor moved past the entry.
         */
        READ
    }
}
