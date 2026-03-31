/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file and the posting lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseLockError;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

/**
 * The write lock of a database directory: an exclusive lock on the lock
 * file, held until released.
 *
 * <p>Within one process a second lock on the same file is refused as it
 * is between processes.</p>
 *
 * @since 0.1.0
 */
public final class WriteLock implements AutoCloseable {

    /**
     * Database directory.
     */
    private final Path dir;

    /**
     * Channel of the lock file.
     */
    private final FileChannel file;

    /**
     * The lock held.
     */
    private final FileLock lock;

    /**
     * Creates a lock held.
     *
     * @param dir Database directory
     * @param channel Channel of the lock file
     * @param lock The lock held
     */
    private WriteLock(
        final Path dir, final FileChannel channel, final FileLock lock
    ) {
        this.dir = dir;
        this.file = channel;
        this.lock = lock;
    }

    /**
     * Acquires the write lock of a directory.
     *
     * @param dir Database directory
     * @param wait True to wait for a lock held elsewhere, false to fail
     * @return Lock held
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static WriteLock acquire(final Path dir, final boolean wait) {
        final FileChannel channel = WriteLock.channel(dir);
        Optional<FileLock> lock = WriteLock.attempt(dir, channel);
        while (lock.isEmpty() && wait) {
            LockSupport.parkNanos(20_000_000L);
            lock = WriteLock.attempt(dir, channel);
        }
        if (lock.isEmpty()) {
            WriteLock.release(channel);
            throw new DatabaseLockError(
                String.format(
                    "Unable to get write lock on %s: already locked", dir
                )
            );
        }
        return new WriteLock(dir, channel, lock.get());
    }

    /**
     * Tests whether a writer holds the lock of a directory.
     *
     * @param dir Database directory
     * @return True when the lock is held, false when free or absent
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static boolean held(final Path dir) {
        boolean result = false;
        try (
            FileChannel channel = FileChannel.open(
                dir.resolve(VidrioFormat.lockFileName()),
                StandardOpenOption.READ, StandardOpenOption.WRITE
            )
        ) {
            final Optional<FileLock> lock = WriteLock.attempt(dir, channel);
            if (lock.isPresent()) {
                lock.get().release();
            } else {
                result = true;
            }
        } catch (final IOException ex) {
            result = false;
        }
        return result;
    }

    /**
     * Tests whether this lock is still held.
     *
     * @return True until released
     */
    public boolean locked() {
        return this.lock.isValid();
    }

    @Override
    public void close() {
        try {
            this.lock.release();
        } catch (final IOException ex) {
            final DatabaseLockError error = new DatabaseLockError(
                String.format("Unable to release write lock on %s", this.dir),
                ex.getMessage()
            );
            error.initCause(ex);
            throw error;
        } finally {
            WriteLock.release(this.file);
        }
    }

    private static FileChannel channel(final Path dir) {
        try {
            return FileChannel.open(
                dir.resolve(VidrioFormat.lockFileName()),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE
            );
        } catch (final IOException ex) {
            final DatabaseLockError error = new DatabaseLockError(
                String.format(
                    "Unable to get write lock on %s: Couldn't open lockfile",
                    dir
                ),
                ex.getMessage()
            );
            error.initCause(ex);
            throw error;
        }
    }

    private static Optional<FileLock> attempt(
        final Path dir, final FileChannel channel
    ) {
        Optional<FileLock> result;
        try {
            result = Optional.ofNullable(channel.tryLock());
        } catch (final OverlappingFileLockException ex) {
            result = Optional.empty();
        } catch (final IOException ex) {
            WriteLock.release(channel);
            final DatabaseLockError error = new DatabaseLockError(
                String.format("Unable to get write lock on %s", dir),
                ex.getMessage()
            );
            error.initCause(ex);
            throw error;
        }
        return result;
    }

    private static void release(final FileChannel channel) {
        try {
            channel.close();
        } catch (final IOException ex) {
            final DatabaseLockError error = new DatabaseLockError(
                "Unable to close lock file", ex.getMessage()
            );
            error.initCause(ex);
            throw error;
        }
    }
}
