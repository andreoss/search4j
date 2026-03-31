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

import io.github.andreoss.search4j.core.backend.Uuid;
import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.DatabaseCreateError;
import io.github.andreoss.search4j.core.error.DatabaseNotFoundError;
import io.github.andreoss.search4j.core.error.DatabaseVersionError;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.ser.Varints;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * The version file of a database directory: magic bytes, format version,
 * the database identifier, the revision, the root information of every
 * table and the database statistics.
 *
 * <p>A new revision is written and synced to a temporary file in the
 * directory, then renamed over the version file.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.TooManyMethods")
public final class VersionFile {

    /**
     * Name of the temporary file a new revision is written to.
     */
    static final String TEMP_NAME = "v.tmp";

    /**
     * Database directory.
     */
    private final Path dir;

    /**
     * Root information of every table at the revision being written.
     */
    private final Map<TableType, TableState> roots;

    /**
     * Root information of every table at the revision last synced.
     */
    private final Map<TableType, TableState> synced;

    /**
     * Identifier of the database.
     */
    private Uuid ident;

    /**
     * Revision last synced.
     */
    private RevisionNumber rev;

    /**
     * Statistics being written.
     */
    private DatabaseStats current;

    /**
     * Statistics as read or last written.
     */
    private DatabaseStats kept;

    /**
     * Creates the version file of a directory, at revision 0 with no
     * tables until {@link #create(BlockSize)} or {@link #read()}.
     *
     * @param dir Database directory
     */
    public VersionFile(final Path dir) {
        this.dir = dir;
        this.roots = new EnumMap<>(TableType.class);
        this.synced = new EnumMap<>(TableType.class);
        this.ident = Uuid.NIL;
        this.rev = new RevisionNumber(0L);
        this.current = DatabaseStats.EMPTY;
        this.kept = DatabaseStats.EMPTY;
    }

    /**
     * Starts a new database: a fresh identifier and empty tables of a
     * block size.
     *
     * @param size Block size of every table
     */
    public void create(final BlockSize size) {
        this.ident = Uuid.generate();
        for (final TableType type : TableType.values()) {
            this.roots.put(type, TableState.initial(size, type.compression()));
        }
        this.synced.clear();
        this.synced.putAll(this.roots);
        this.current = DatabaseStats.EMPTY;
        this.kept = DatabaseStats.EMPTY;
    }

    /**
     * Reads the version file of the directory.
     */
    public void read() {
        final Path path = this.dir.resolve(VidrioFormat.versionFileName());
        final byte[] data;
        try {
            data = Files.readAllBytes(path);
        } catch (final NoSuchFileException ex) {
            final DatabaseNotFoundError error = new DatabaseNotFoundError(
                String.format("%s: no version file to read", this.dir),
                ex.getMessage()
            );
            error.initCause(ex);
            throw error;
        } catch (final IOException ex) {
            final DatabaseCorruptError error = new DatabaseCorruptError(
                String.format("%s: version file not readable", this.dir),
                ex.getMessage()
            );
            error.initCause(ex);
            throw error;
        }
        this.parse(data);
    }

    /**
     * Writes a new revision to a temporary file in the directory and
     * syncs it to disk, to be renamed over the version file by
     * {@link #sync(Path, RevisionNumber)}.
     *
     * @param revision Revision written
     * @return The temporary file
     */
    public Path write(final RevisionNumber revision) {
        final byte[] data = this.bytes(revision);
        this.kept = this.current;
        final Path temp = this.dir.resolve(VersionFile.TEMP_NAME);
        try (
            FileChannel channel = FileChannel.open(
                temp, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE
            )
        ) {
            final ByteBuffer buf = ByteBuffer.wrap(data);
            while (buf.hasRemaining()) {
                channel.write(buf);
            }
            channel.force(true);
        } catch (final IOException ex) {
            final DatabaseCreateError error = new DatabaseCreateError(
                String.format("Couldn't write new version file %s", temp),
                ex.getMessage()
            );
            error.initCause(ex);
            throw error;
        }
        return temp;
    }

    /**
     * Renames the temporary file over the version file; on failure the
     * temporary file is removed.
     *
     * @param temp File from {@link #write(RevisionNumber)}
     * @param revision Revision written, above the synced one unless 0
     * @return Whether the revision is now the version file
     */
    public boolean sync(final Path temp, final RevisionNumber revision) {
        if (revision.compareTo(this.rev) <= 0 && this.rev.value() != 0L) {
            throw new InvalidArgumentError(
                String.format(
                    "Revision %s not above the synced %s", revision, this.rev
                )
            );
        }
        boolean done;
        try {
            Files.move(
                temp, this.dir.resolve(VidrioFormat.versionFileName()),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
            done = true;
        } catch (final IOException ex) {
            VersionFile.remove(temp);
            done = false;
        }
        if (done) {
            this.synced.clear();
            this.synced.putAll(this.roots);
            this.rev = revision;
        }
        return done;
    }

    /**
     * Drops the root information changed since the last sync and the
     * statistics changed since the last write or read.
     */
    public void cancel() {
        this.roots.clear();
        this.roots.putAll(this.synced);
        this.current = this.kept;
    }

    /**
     * Revision last synced.
     *
     * @return Revision, 0 for a new database
     */
    public RevisionNumber revision() {
        return this.rev;
    }

    /**
     * Identifier of the database.
     *
     * @return Identifier, {@link Uuid#NIL} before creation or reading
     */
    public Uuid uuid() {
        return this.ident;
    }

    /**
     * Root information of a table at the revision being written.
     *
     * @param type Table wanted
     * @return Root information
     */
    public TableState root(final TableType type) {
        final TableState state = this.roots.get(type);
        if (state == null) {
            throw new InvalidOperationError(
                "Version file neither created nor read"
            );
        }
        return state;
    }

    /**
     * Sets the root information of a table for the revision being written.
     *
     * @param type Table wanted
     * @param state State committed by the table
     */
    public void root(final TableType type, final TableState state) {
        this.roots.put(type, state);
    }

    /**
     * Statistics of the revision being written.
     *
     * @return Statistics being written
     */
    public DatabaseStats stats() {
        return this.current;
    }

    /**
     * Sets the statistics of the revision being written.
     *
     * @param updated Statistics replacing the current ones
     */
    public void stats(final DatabaseStats updated) {
        this.current = updated;
    }

    /**
     * Decodes a version record.
     *
     * @param data Record bytes, the whole version file
     */
    void parse(final byte[] data) {
        if (data.length < VidrioFormat.MAGIC_BYTES + 2 + Uuid.BYTES + 1) {
            throw new DatabaseCorruptError("Couldn't read enough (EOF)");
        }
        final byte[] magic = VidrioFormat.versionMagic();
        if (!Arrays.equals(data, 0, magic.length, magic, 0, magic.length)) {
            throw new DatabaseCorruptError("Version file magic incorrect");
        }
        final int version = ByteBuffer.wrap(data).getShort(magic.length)
            & 0xFFFF;
        if (version != VidrioFormat.FORMAT_VERSION) {
            throw new DatabaseVersionError(
                String.format(
                    "%s: Database is format version %d but only %d is known",
                    this.dir, VidrioFormat.versionDate(version),
                    VidrioFormat.versionDate(VidrioFormat.FORMAT_VERSION)
                )
            );
        }
        final int start = magic.length + 2;
        this.ident = Uuid.of(
            Arrays.copyOfRange(data, start, start + Uuid.BYTES)
        );
        final ByteReader reader = ByteReader.over(
            data, start + Uuid.BYTES, data.length
        );
        this.rev = new RevisionNumber(reader.narrow("Version file revision"));
        for (final TableType type : TableType.values()) {
            this.roots.put(type, TableState.parse(reader, this.rev));
        }
        this.synced.clear();
        this.synced.putAll(this.roots);
        this.kept = DatabaseStats.parse(reader, false);
        this.current = this.kept;
    }

    /**
     * Encodes the version record of a revision.
     *
     * @param revision Revision written
     * @return Record bytes
     */
    byte[] bytes(final RevisionNumber revision) {
        if (this.roots.isEmpty()) {
            throw new InvalidOperationError(
                "Version file neither created nor read"
            );
        }
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(VidrioFormat.versionMagic());
        out.write(VidrioFormat.FORMAT_VERSION >> Byte.SIZE);
        out.write(VidrioFormat.FORMAT_VERSION);
        out.writeBytes(this.ident.bytes());
        out.writeBytes(Varints.pack(revision.value()));
        for (final TableType type : TableType.values()) {
            out.writeBytes(this.roots.get(type).bytes());
        }
        out.writeBytes(this.current.bytes());
        return out.toByteArray();
    }

    private static void remove(final Path temp) {
        try {
            Files.deleteIfExists(temp);
        } catch (final IOException ex) {
            final DatabaseCorruptError error = new DatabaseCorruptError(
                String.format("Couldn't remove %s", temp), ex.getMessage()
            );
            error.initCause(ex);
            throw error;
        }
    }
}
