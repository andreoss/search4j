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

import io.github.andreoss.search4j.core.error.DatabaseClosedError;
import io.github.andreoss.search4j.core.error.DatabaseError;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import java.util.Optional;

/**
 * A B-tree table opened to write revisions.
 *
 * <p>Changes go to blocks not used by the revision open, which stays
 * readable until a later revision frees its blocks; {@link #commit}
 * writes the changed blocks and returns the root information of the new
 * revision. A tag longer than an item is split into components, and is
 * compressed first when longer than the threshold and smaller once
 * compressed. Not thread-safe.</p>
 *
 * @since 0.1.0
 */
public final class TableWriter implements AutoCloseable, TableLookup {

    /**
     * Store of the blocks.
     */
    private final BlockStore store;

    /**
     * Free list.
     */
    private final FreeBlocks free;

    /**
     * Path being changed.
     */
    private final Spine spine;

    /**
     * Item changes.
     */
    private final TreeEdit edit;

    /**
     * Entries of the tree.
     */
    private final Entries entries;

    /**
     * Smallest tag length compressed, 0 for none.
     */
    private final int compress;

    /**
     * Number of entries.
     */
    private long count;

    /**
     * Whether there are changes since the last commit.
     */
    private boolean changed;

    /**
     * Whether the table was closed.
     */
    private boolean closed;

    /**
     * Creates a table over its parts.
     *
     * @param store Store of the blocks
     * @param free Free list
     * @param spine Path, open at the root
     * @param edit Item changes
     * @param entries Entries of the tree
     * @param compress Smallest tag length compressed, 0 for none
     * @param count Number of entries
     */
    private TableWriter(
        final BlockStore store, final FreeBlocks free, final Spine spine,
        final TreeEdit edit, final Entries entries, final int compress,
        final long count
    ) {
        this.store = store;
        this.free = free;
        this.spine = spine;
        this.edit = edit;
        this.entries = entries;
        this.compress = compress;
        this.count = count;
    }

    /**
     * Creates an empty table over a store, at revision 0.
     *
     * @param store Store of the blocks, empty
     * @param compress Smallest tag length compressed, 0 for none
     * @return Table open to write
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static TableWriter create(final BlockStore store, final int compress) {
        return TableWriter.open(
            store, TableState.initial(store.size(), compress)
        );
    }

    /**
     * Opens a table to write at a committed revision.
     *
     * <p>The store is closed if the root cannot be read.</p>
     *
     * @param store Store of the blocks, of the state's block size
     * @param state Root information of the revision
     * @return Table open to write
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static TableWriter open(final BlockStore store, final TableState state) {
        if (!store.size().equals(state.root().size())) {
            store.close();
            throw new IllegalArgumentException(
                String.format("Block size mismatch: %s", state.root().size())
            );
        }
        try {
            final FreeBlocks free = FreeBlocks.of(store, state.free());
            final Spine spine = new Spine(store, free);
            spine.open(state.root());
            final TreeEdit edit =
                new TreeEdit(spine, free, store.size().bytes());
            edit.rewind(state.sequential());
            return new TableWriter(
                store, free, spine, edit,
                Entries.of(spine, edit, state), state.compress(),
                state.entries().value()
            );
        } catch (final DatabaseError ex) {
            store.close();
            throw ex;
        }
    }

    /**
     * Adds an entry, replacing any entry with the same key.
     *
     * <p>The empty key names the root item, which is replaced.</p>
     *
     * @param key Key bytes, at most {@link ItemKey#MAX_LENGTH}
     * @param tag Tag bytes
     */
    public void add(final byte[] key, final byte[] tag) {
        this.put(key, tag, false);
    }

    /**
     * Adds an entry whose tag is already compressed, replacing any entry
     * with the same key.
     *
     * @param key Key bytes, at most {@link ItemKey#MAX_LENGTH}
     * @param tag Raw deflate stream of the tag
     */
    public void addCompressed(final byte[] key, final byte[] tag) {
        this.put(key, tag, true);
    }

    /**
     * Deletes an entry.
     *
     * @param key Key bytes
     * @return True if the entry existed; false for the empty key and keys
     *  too long to store
     */
    public boolean delete(final byte[] key) {
        this.check();
        final boolean done = key.length > 0
            && key.length <= ItemKey.MAX_LENGTH && this.entries.delete(key);
        if (done) {
            this.count -= 1L;
            this.changed = true;
        }
        return done;
    }

    /**
     * Number of entries, the root item not counted.
     *
     * @return Entry count
     */
    public EntryCount entries() {
        return new EntryCount(this.count);
    }

    /**
     * Whether there are changes since the last commit or cancel.
     *
     * @return True once an entry was added or deleted
     */
    public boolean modified() {
        return this.changed;
    }

    /**
     * Sets full compaction: blocks are filled as much as possible, for
     * entries added in key order.
     *
     * @param flag True to fill blocks
     */
    public void compaction(final boolean flag) {
        this.check();
        this.entries.compaction(flag);
    }

    @Override
    public TableCursor cursor() {
        this.check();
        return new TableCursor(this.store, this.spine.root());
    }

    @Override
    public Optional<byte[]> get(final byte[] key) {
        final TableCursor cursor = this.cursor();
        Optional<byte[]> tag = Optional.empty();
        if (cursor.exact(key)) {
            tag = Optional.of(cursor.tag());
        }
        return tag;
    }

    @Override
    public boolean contains(final byte[] key) {
        return this.cursor().seek(key);
    }

    /**
     * Writes the changed blocks and the free list as a new revision, forces
     * them to durable storage, then continues from it.
     *
     * <p>On a failure the table is closed.</p>
     *
     * @param revision New revision, above the one open
     * @return Root information of the new revision
     */
    public TableState commit(final RevisionNumber revision) {
        this.check();
        if (revision.value() <= this.spine.revision()) {
            throw new InvalidArgumentError("New revision too low");
        }
        try {
            this.spine.flush();
            final int level = this.spine.level();
            final TableRoot root = new TableRoot(
                new BlockNumber(this.spine.number(level)), level,
                this.store.size(), revision, false
            );
            this.free.commit(revision.value());
            this.store.sync();
            final TableState state = new TableState(
                root, new EntryCount(this.count), this.edit.sequential(),
                this.compress, this.free.info()
            );
            this.spine.open(root);
            this.edit.rewind(state.sequential());
            this.changed = false;
            return state;
        } catch (final DatabaseError ex) {
            this.close();
            throw ex;
        }
    }

    /**
     * Drops the changes since a committed revision and continues from it.
     *
     * @param state Root information of the revision
     */
    public void cancel(final TableState state) {
        this.check();
        if (!this.store.size().equals(state.root().size())) {
            throw new IllegalArgumentException(
                String.format("Block size mismatch: %s", state.root().size())
            );
        }
        this.count = state.entries().value();
        this.free.restore(state.free());
        this.spine.open(state.root());
        this.edit.rewind(state.sequential());
        this.changed = false;
    }

    @Override
    public void close() {
        this.closed = true;
        this.store.close();
    }

    private void put(final byte[] key, final byte[] tag, final boolean packed) {
        this.check();
        if (!this.entries.put(key, tag, packed)) {
            this.count += 1L;
        }
        this.changed = true;
    }

    private void check() {
        if (this.closed) {
            throw new DatabaseClosedError("Database has been closed");
        }
    }
}
