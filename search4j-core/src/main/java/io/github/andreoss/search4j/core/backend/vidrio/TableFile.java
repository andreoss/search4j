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
import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.DatabaseCreateError;
import io.github.andreoss.search4j.core.error.DatabaseNotFoundError;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.NonWritableChannelException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Blocks of a table file, read and written with positioned I/O.
 *
 * @since 0.1.0
 */
public final class TableFile implements BlockStore {

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
        this(TableFile.reading(path), size);
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

    /**
     * Creates an empty table file to read and write, replacing any file.
     *
     * @param path Table file
     * @param size Block size of the table
     * @return File open to read and write
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static TableFile create(final Path path, final BlockSize size) {
        try {
            return new TableFile(
                FileChannel.open(
                    path, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.READ, StandardOpenOption.WRITE
                ),
                size
            );
        } catch (final IOException ex) {
            final DatabaseCreateError error = new DatabaseCreateError(
                String.format("Couldn't create %s read/write", path.getFileName()),
                ex.getMessage()
            );
            error.initCause(ex);
            throw error;
        }
    }

    /**
     * Opens an existing table file to read and write.
     *
     * @param path Table file
     * @param size Block size of the table
     * @return File open to read and write
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static TableFile update(final Path path, final BlockSize size) {
        return new TableFile(
            TableFile.opened(
                path, "read/write", StandardOpenOption.READ,
                StandardOpenOption.WRITE
            ),
            size
        );
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
    public void write(final BlockNumber number, final byte[] data) {
        if (data.length != this.bsize.bytes()) {
            throw new IllegalArgumentException(
                String.format("Bad block length: %d", data.length)
            );
        }
        if (!this.channel.isOpen()) {
            throw new DatabaseClosedError("Database has been closed");
        }
        final ByteBuffer buf = ByteBuffer.wrap(data);
        final long start = number.value() * this.bsize.bytes();
        try {
            while (buf.hasRemaining()) {
                this.channel.write(buf, start + buf.position());
            }
        } catch (final NonWritableChannelException ex) {
            final InvalidOperationError error =
                new InvalidOperationError("Table opened to read");
            error.initCause(ex);
            throw error;
        } catch (final IOException ex) {
            final DatabaseCorruptError error = new DatabaseCorruptError(
                String.format("Error writing block %s", number),
                ex.getMessage()
            );
            error.initCause(ex);
            throw error;
        }
    }

    @Override
    public void sync() {
        try {
            this.channel.force(false);
        } catch (final IOException ex) {
            final DatabaseCorruptError error = new DatabaseCorruptError(
                "Error syncing table", ex.getMessage()
            );
            error.initCause(ex);
            throw error;
        }
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

    private static FileChannel reading(final Path path) {
        return TableFile.opened(path, "to read", StandardOpenOption.READ);
    }

    private static FileChannel opened(
        final Path path, final String mode, final OpenOption... options
    ) {
        try {
            return FileChannel.open(path, options);
        } catch (final IOException ex) {
            final DatabaseNotFoundError error = new DatabaseNotFoundError(
                String.format("Couldn't open %s %s", path.getFileName(), mode),
                ex.getMessage()
            );
            error.initCause(ex);
            throw error;
        }
    }
}
