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

import io.github.andreoss.search4j.core.error.DatabaseCreateError;
import io.github.andreoss.search4j.core.error.DatabaseError;
import io.github.andreoss.search4j.core.types.DatabaseAction;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A database directory held for writing: its lock, version file, tables,
 * value changes and inverter, with the commit and cancel of a revision.
 *
 * @since 0.1.0
 */
public final class WriteSession implements TableSet {

    /**
     * Table files of the directory.
     */
    private final TableFiles files;

    /**
     * Write lock held.
     */
    private final WriteLock lock;

    /**
     * Version file of the revision being written.
     */
    private final VersionFile version;

    /**
     * Buffered posting, length and position changes.
     */
    private final Inverter inverter;

    /**
     * The tables.
     */
    private WriteTables tables;

    /**
     * Buffered value changes.
     */
    private ValueWriter values;

    /**
     * Creates the session from its parts.
     *
     * @param files Table files of the directory
     * @param lock Write lock held
     * @param version Version file created or read
     */
    private WriteSession(
        final TableFiles files, final WriteLock lock, final VersionFile version
    ) {
        this.files = files;
        this.lock = lock;
        this.version = version;
        this.inverter = new Inverter();
    }

    /**
     * Opens a database directory to write as the options say: created,
     * overwritten or opened.
     *
     * @param dir Database directory
     * @param options Opening options
     * @return Session holding the write lock
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static WriteSession open(final Path dir, final VidrioOptions options) {
        final TableFiles files = new TableFiles(dir);
        final DatabaseAction action = options.action();
        final boolean exists = files.exists(TableType.POSTLIST);
        if (action == DatabaseAction.OPEN && !exists) {
            throw files.missing();
        }
        if (action == DatabaseAction.CREATE && exists) {
            throw new DatabaseCreateError(
                String.format(
                    "Can't create new database at '%s': a database already exists and I was told not to overwrite it",
                    dir
                )
            );
        }
        if (!exists) {
            WriteSession.mkdir(dir);
        }
        final WriteSession session = new WriteSession(
            files, WriteLock.acquire(dir, options.retry()), new VersionFile(dir)
        );
        try {
            if (exists && action != DatabaseAction.CREATE_OR_OVERWRITE) {
                session.version.read();
                session.tables = WriteTables.open(files, session.version);
            } else {
                session.create(options.size());
            }
        } catch (final DatabaseError ex) {
            session.lock.close();
            throw ex;
        }
        session.values = session.freshValues();
        return session;
    }

    /**
     * Database directory.
     *
     * @return Database directory
     */
    public Path dir() {
        return this.files.dir();
    }

    /**
     * Version file of the revision being written.
     *
     * @return Version file being written
     */
    public VersionFile version() {
        return this.version;
    }

    /**
     * The tables.
     *
     * @return Tables open
     */
    public WriteTables tables() {
        return this.tables;
    }

    /**
     * Buffered posting, length and position changes.
     *
     * @return Pending posting changes
     */
    public Inverter inverter() {
        return this.inverter;
    }

    /**
     * Buffered value changes.
     *
     * @return Value changes
     */
    public ValueWriter values() {
        return this.values;
    }

    @Override
    public TableLookup lookup(final TableType type) {
        return this.tables.lookup(type);
    }

    @Override
    public EntryCount entries(final TableType type) {
        return this.tables.entries(type);
    }

    /**
     * Merges the inverter's changes into the tables; on failure every
     * pending change is dropped.
     */
    public void flush() {
        boolean done = false;
        try {
            this.inverter.flush(
                new PostingWriter(this.tables.writer(TableType.POSTLIST))
            );
            if (this.inverter.positions().pending()) {
                this.inverter.positions().flush(
                    this.tables.writer(TableType.POSITION)
                );
            }
            done = true;
        } finally {
            if (!done) {
                this.cancel();
            }
        }
    }

    /**
     * Commits the changes of the tables as the next revision: value
     * changes merged, tables written and forced to disk, then the version
     * file; nothing happens without changes.
     *
     * <p>On failure the tables are reopened at the revision before.</p>
     */
    public void apply() {
        if (this.tables.modified() || this.values.modified()) {
            final RevisionNumber next =
                new RevisionNumber(this.version.revision().value() + 1L);
            try {
                this.values.merge();
                this.tables.commit(next);
                if (!this.version.sync(this.version.write(next), next)) {
                    throw new DatabaseCreateError("Commit failed");
                }
            } catch (final DatabaseError ex) {
                this.recover(ex);
                throw ex;
            }
        }
    }

    /**
     * Drops every pending change and the uncommitted changes of the tables.
     */
    public void cancel() {
        this.version.cancel();
        this.tables.cancel();
        this.values.cancel();
        this.inverter.clear();
    }

    @Override
    public void close() {
        try {
            this.tables.close();
        } finally {
            this.lock.close();
        }
    }

    private void create(final BlockSize size) {
        this.version.create(size);
        final RevisionNumber zero = this.version.revision();
        final Path temp = this.version.write(zero);
        this.tables = WriteTables.create(this.files, this.version);
        if (!this.version.sync(temp, zero)) {
            this.tables.close();
            throw new DatabaseCreateError("Failed to create version file");
        }
    }

    private void recover(final DatabaseError cause) {
        this.inverter.clear();
        this.tables.close();
        try {
            this.version.read();
            this.tables = WriteTables.open(this.files, this.version);
            this.values = this.freshValues();
        } catch (final DatabaseError ex) {
            this.lock.close();
            final DatabaseCreateError error = new DatabaseCreateError(
                String.format(
                    "Modifications failed (%s), and couldn't open at the old revision: %s",
                    cause.getMessage(), ex.getMessage()
                )
            );
            error.initCause(ex);
            throw error;
        }
    }

    private ValueWriter freshValues() {
        return new ValueWriter(
            this.tables.writer(TableType.POSTLIST),
            this.tables.writer(TableType.TERMLIST)
        );
    }

    private static void mkdir(final Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (final IOException ex) {
            final DatabaseCreateError error = new DatabaseCreateError(
                String.format("%s: mkdir failed", dir), ex.getMessage()
            );
            error.initCause(ex);
            throw error;
        }
    }
}
