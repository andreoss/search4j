/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk table format definitions of the vidrio backend.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseClosedError;
import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.DatabaseNotFoundError;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Blocks of a table file, read with positioned reads.
 *
 * @since 0.1.0
 */
public final class TableFile implements Blocks {

    /**
     * Channel of the open file.
     */
    private final FileChannel channel;

    /**
     * Block size of the file.
     */
    private final BlockSize bsize;

    /**
     * Opens a table file to read.
     *
     * @param path Table file
     * @param size Block size of the table
     */
    public TableFile(final Path path, final BlockSize size) {
        this(TableFile.opened(path), size);
    }

    /**
     * Creates a source over an open channel.
     *
     * @param channel Channel to read
     * @param size Block size of the table
     */
    private TableFile(final FileChannel channel, final BlockSize size) {
        this.channel = channel;
        this.bsize = size;
    }

    @Override
    public BlockSize size() {
        return this.bsize;
    }

    @Override
    public Block read(final BlockNumber number) {
        if (!this.channel.isOpen()) {
            throw new DatabaseClosedError("Database has been closed");
        }
        final ByteBuffer buf = ByteBuffer.allocate(this.bsize.bytes());
        final long start = number.value() * this.bsize.bytes();
        try {
            while (buf.hasRemaining()) {
                if (this.channel.read(buf, start + buf.position()) < 0) {
                    throw new DatabaseCorruptError(
                        String.format("EOF reading block %s", number)
                    );
                }
            }
        } catch (final IOException ex) {
            final DatabaseCorruptError error = new DatabaseCorruptError(
                String.format("Error reading block %s", number),
                ex.getMessage()
            );
            error.initCause(ex);
            throw error;
        }
        return Block.parse(number, buf.array());
    }

    @Override
    public void close() {
        try {
            this.channel.close();
        } catch (final IOException ex) {
            final DatabaseCorruptError error = new DatabaseCorruptError(
                "Error closing table", ex.getMessage()
            );
            error.initCause(ex);
            throw error;
        }
    }

    private static FileChannel opened(final Path path) {
        try {
            return FileChannel.open(path, StandardOpenOption.READ);
        } catch (final IOException ex) {
            final DatabaseNotFoundError error = new DatabaseNotFoundError(
                String.format("Couldn't open %s to read", path.getFileName()),
                ex.getMessage()
            );
            error.initCause(ex);
            throw error;
        }
    }
}
