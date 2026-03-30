/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Blocks held in memory, for trees built and written by tests.
 *
 * @since 0.1.0
 */
final class MemoryBlocks implements BlockStore {

    /**
     * Block bytes by block number.
     */
    private final Map<Long, byte[]> data;

    /**
     * Block size.
     */
    private final BlockSize bsize;

    /**
     * Number of reads.
     */
    private final AtomicInteger reads;

    /**
     * Number of closes.
     */
    private final AtomicInteger closes;

    /**
     * Whether writes fail.
     */
    private final AtomicBoolean failing;

    /**
     * Creates a source.
     *
     * @param data Block bytes by number, shared
     * @param size Block size
     */
    MemoryBlocks(final Map<Long, byte[]> data, final BlockSize size) {
        this.data = data;
        this.bsize = size;
        this.reads = new AtomicInteger();
        this.closes = new AtomicInteger();
        this.failing = new AtomicBoolean();
    }

    @Override
    public BlockSize size() {
        return this.bsize;
    }

    @Override
    public Block read(final BlockNumber number) {
        if (!this.data.containsKey(number.value())) {
            throw new DatabaseCorruptError(
                String.format("EOF reading block %s", number)
            );
        }
        this.reads.incrementAndGet();
        return Block.parse(number, this.data.get(number.value()).clone());
    }

    @Override
    public void write(final BlockNumber number, final byte[] bytes) {
        if (bytes.length != this.bsize.bytes()) {
            throw new IllegalArgumentException("Bad block length");
        }
        if (this.failing.get()) {
            throw new DatabaseCorruptError("Error writing block");
        }
        this.data.put(number.value(), bytes.clone());
    }

    @Override
    public void close() {
        this.closes.incrementAndGet();
    }

    /**
     * Bytes of a block, shared so tests can damage them.
     *
     * @param number Block number
     * @return Block bytes
     */
    byte[] bytes(final long number) {
        return this.data.get(number);
    }

    /**
     * Number of blocks.
     *
     * @return Count
     */
    int count() {
        return this.data.size();
    }

    /**
     * Number of reads so far.
     *
     * @return Count
     */
    int reads() {
        return this.reads.get();
    }

    /**
     * Makes writes fail or succeed.
     *
     * @param flag True to fail
     */
    void broken(final boolean flag) {
        this.failing.set(flag);
    }

    /**
     * Number of closes so far.
     *
     * @return Count
     */
    int closes() {
        return this.closes.get();
    }
}
