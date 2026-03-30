/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;

/**
 * Random changes applied to a table and to a sorted map model.
 *
 * @since 0.1.0
 */
final class Workload {

    /**
     * Letters of the keys, few so that keys share prefixes.
     */
    private static final byte[] LETTERS = Trees.key("abcxyz");

    /**
     * Source of randomness.
     */
    private final Random random;

    /**
     * Blocks written.
     */
    private final MemoryBlocks blocks;

    /**
     * Expected entries.
     */
    private final NavigableMap<byte[], byte[]> model;

    /**
     * Creates a workload over an empty store.
     *
     * @param seed Seed of the randomness
     * @param size Block size
     */
    Workload(final long seed, final BlockSize size) {
        this.random = new Random(seed);
        this.blocks = new MemoryBlocks(new HashMap<>(), size);
        this.model = new TreeMap<>(Arrays::compareUnsigned);
    }

    /**
     * Blocks written.
     *
     * @return Store
     */
    MemoryBlocks blocks() {
        return this.blocks;
    }

    /**
     * Expected entries.
     *
     * @return Model, shared
     */
    NavigableMap<byte[], byte[]> model() {
        return this.model;
    }

    /**
     * Applies random additions and deletions.
     *
     * @param table Table written
     * @param count Number of changes
     * @param deletes Percentage of deletions
     */
    void apply(final TableWriter table, final int count, final int deletes) {
        for (int idx = 0; idx < count; ++idx) {
            if (this.random.nextInt(100) < deletes && !this.model.isEmpty()) {
                final byte[] key = this.pick();
                final boolean present = this.model.remove(key) != null;
                if (table.delete(key) != present) {
                    throw new IllegalStateException("Delete result differs");
                }
            } else {
                final byte[] key = this.key();
                final byte[] tag = this.tag();
                this.model.put(key, tag);
                table.add(key, tag);
            }
        }
    }

    /**
     * Random key.
     *
     * @return Key of 1 to 40 bytes
     */
    byte[] key() {
        final byte[] key = new byte[1 + this.random.nextInt(40)];
        for (int idx = 0; idx < key.length; ++idx) {
            key[idx] = Workload.LETTERS[this.random.nextInt(Workload.LETTERS.length)];
        }
        return key;
    }

    /**
     * Random tag: mostly short, sometimes several items long, sometimes
     * compressible.
     *
     * @return Tag bytes
     */
    byte[] tag() {
        final int kind = this.random.nextInt(20);
        final byte[] tag;
        if (kind == 0) {
            tag = new byte[600 + this.random.nextInt(3000)];
            this.random.nextBytes(tag);
        } else if (kind == 1) {
            tag = new byte[100 + this.random.nextInt(4000)];
            Arrays.fill(tag, (byte) this.random.nextInt(3));
        } else {
            tag = new byte[this.random.nextInt(60)];
            this.random.nextBytes(tag);
        }
        return tag;
    }

    /**
     * Entries of a committed revision read back in key order, each as its
     * key and tag in hex, the root item left out.
     *
     * @param state Revision
     * @return Entries
     */
    List<String> read(final TableState state) {
        final List<String> found = new ArrayList<>(this.model.size());
        final VidrioTable table = VidrioTable.over(this.blocks, state.root());
        final TableCursor cursor = table.cursor();
        cursor.rewind();
        while (cursor.next()) {
            found.add(Workload.entry(cursor.key().data(), cursor.tag()));
        }
        return found;
    }

    /**
     * Expected entries in key order, as {@link #read(TableState)} gives.
     *
     * @return Entries
     */
    List<String> expected() {
        final List<String> expected = new ArrayList<>(this.model.size());
        for (final Map.Entry<byte[], byte[]> entry : this.model.entrySet()) {
            expected.add(Workload.entry(entry.getKey(), entry.getValue()));
        }
        return expected;
    }

    private byte[] pick() {
        final byte[] key;
        if (this.random.nextInt(4) == 0) {
            key = this.key();
        } else {
            final List<byte[]> keys = new ArrayList<>(this.model.keySet());
            key = keys.get(this.random.nextInt(keys.size()));
        }
        return key;
    }

    private static String entry(final byte[] key, final byte[] tag) {
        return String.format(
            "%s=%s", HexFormat.of().formatHex(key),
            HexFormat.of().formatHex(tag)
        );
    }
}
