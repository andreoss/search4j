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

import io.github.andreoss.search4j.core.error.DatabaseError;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * The tables of a database opened to write.
 *
 * <p>A table created on its first write is opened when first written;
 * until then it reads as empty. Every table open is committed, so an
 * untouched table opened at creation gets its root block on the first
 * commit, as the reference writes it.</p>
 *
 * @since 0.1.0
 */
public final class WriteTables implements TableSet {

    /**
     * Table files of the directory.
     */
    private final TableFiles files;

    /**
     * Version file holding the root information.
     */
    private final VersionFile version;

    /**
     * Tables open, by type.
     */
    private final Map<TableType, TableWriter> writers;

    /**
     * Creates the set from its parts.
     *
     * @param files Table files of the directory
     * @param version Version file holding the root information
     * @param writers Tables open, by type
     */
    private WriteTables(
        final TableFiles files, final VersionFile version,
        final Map<TableType, TableWriter> writers
    ) {
        this.files = files;
        this.version = version;
        this.writers = writers;
    }

    /**
     * Creates the tables of a new database from a created version file:
     * the eager tables are created, the others removed.
     *
     * @param files Table files of the directory
     * @param version Version file created
     * @return Tables open to write
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static WriteTables create(
        final TableFiles files, final VersionFile version
    ) {
        final Map<TableType, TableWriter> open = new EnumMap<>(TableType.class);
        try {
            for (final TableType type : TableType.values()) {
                if (type.laziness() == TableLaziness.LAZY) {
                    files.remove(type);
                } else {
                    open.put(type, files.create(type, version.root(type)));
                }
            }
        } catch (final DatabaseError ex) {
            WriteTables.closeAll(open);
            throw ex;
        }
        return new WriteTables(files, version, open);
    }

    /**
     * Opens the tables of a database at the revision of a version file
     * read.
     *
     * @param files Table files of the directory
     * @param version Version file read
     * @return Tables open to write
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static WriteTables open(
        final TableFiles files, final VersionFile version
    ) {
        final Map<TableType, TableWriter> open = new EnumMap<>(TableType.class);
        try {
            for (final TableType type : TableType.values()) {
                final TableState state = version.root(type);
                if (files.exists(type)) {
                    open.put(type, files.update(type, state));
                } else if (type.laziness() == TableLaziness.EAGER
                    || !state.root().fake()) {
                    files.read(type, state);
                }
            }
        } catch (final DatabaseError ex) {
            WriteTables.closeAll(open);
            throw ex;
        }
        return new WriteTables(files, version, open);
    }

    /**
     * Writer of a table, opening the table when it is created on its first
     * write.
     *
     * @param type Table wanted
     * @return Writer, opened if needed
     */
    public TableWriter writer(final TableType type) {
        return this.writers.computeIfAbsent(
            type, key -> this.files.create(key, this.version.root(key))
        );
    }

    /**
     * Writer of a table already open.
     *
     * @param type Table wanted
     * @return Writer, empty for a table created on its first write and
     *  not written yet
     */
    public Optional<TableWriter> opened(final TableType type) {
        return Optional.ofNullable(this.writers.get(type));
    }

    @Override
    public TableLookup lookup(final TableType type) {
        final TableLookup result;
        final TableWriter writer = this.writers.get(type);
        if (writer == null) {
            final TableRoot root = this.version.root(type).root();
            result = VidrioTable.over(new EmptyBlocks(root.size()), root);
        } else {
            result = writer;
        }
        return result;
    }

    @Override
    public EntryCount entries(final TableType type) {
        final EntryCount result;
        final TableWriter writer = this.writers.get(type);
        if (writer == null) {
            result = this.version.root(type).entries();
        } else {
            result = writer.entries();
        }
        return result;
    }

    /**
     * Tests whether any table changed since the last commit or cancel.
     *
     * @return True when an entry was added or deleted
     */
    public boolean modified() {
        boolean result = false;
        for (final TableWriter writer : this.writers.values()) {
            result = result || writer.modified();
        }
        return result;
    }

    /**
     * Commits every table open as a new revision and records the root
     * information in the version file.
     *
     * @param revision New revision
     */
    public void commit(final RevisionNumber revision) {
        for (final Map.Entry<TableType, TableWriter> entry
            : this.writers.entrySet()) {
            this.version.root(entry.getKey(), entry.getValue().commit(revision));
        }
    }

    /**
     * Drops the changes of every table open since the revision the version
     * file holds.
     */
    public void cancel() {
        for (final Map.Entry<TableType, TableWriter> entry
            : this.writers.entrySet()) {
            entry.getValue().cancel(this.version.root(entry.getKey()));
        }
    }

    @Override
    public void close() {
        WriteTables.closeAll(this.writers);
    }

    private static void closeAll(final Map<TableType, TableWriter> open) {
        for (final TableWriter writer : open.values()) {
            writer.close();
        }
    }
}
