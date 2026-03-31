/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.backend.EmptyShard;
import io.github.andreoss.search4j.core.backend.Shard;
import io.github.andreoss.search4j.core.backend.ShardDatabase;
import io.github.andreoss.search4j.core.backend.StubEntry;
import io.github.andreoss.search4j.core.backend.StubFile;
import io.github.andreoss.search4j.core.backend.StubLocal;
import io.github.andreoss.search4j.core.backend.StubMemory;
import io.github.andreoss.search4j.core.backend.inmemory.InMemoryShard;
import io.github.andreoss.search4j.core.backend.vidrio.VidrioFormat;
import io.github.andreoss.search4j.core.backend.vidrio.VidrioOptions;
import io.github.andreoss.search4j.core.backend.vidrio.VidrioShard;
import io.github.andreoss.search4j.core.backend.vidrio.VidrioWritableShard;
import io.github.andreoss.search4j.core.error.DatabaseNotFoundError;
import io.github.andreoss.search4j.core.error.UnimplementedError;
import io.github.andreoss.search4j.core.types.Backend;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Factory for opening databases and creating documents.
 *
 * <p>An in-memory database needs no path. A path names a vidrio database
 * directory or a stub file listing one database; a stub listing a remote
 * database or several databases is not supported.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class DatabaseFactory {

    /**
     * Utility class, no instances.
     */
    private DatabaseFactory() {
    }

    /**
     * Creates an empty writable database held in memory.
     *
     * @return New database, independent of any other
     */
    public static WritableDatabase inMemory() {
        return new ShardDatabase(new InMemoryShard());
    }

    /**
     * Creates an empty document belonging to no database.
     *
     * @return New editable document
     */
    public static Document document() {
        return new DocumentInternal();
    }

    /**
     * Opens a writable database at a path, creating it as the flags say.
     *
     * <p>Without backend bits a stub file opens the database it lists and
     * any other path is a vidrio directory.</p>
     *
     * @param path Database directory or stub file, not null
     * @param flags Bitwise-or of action, backend and opening flags
     * @return Writable database
     */
    public static WritableDatabase openWritable(
        final Path path,
        final int flags
    ) {
        return DatabaseFactory.writable(path, VidrioOptions.of(flags));
    }

    /**
     * Opens a writable database at a path with the block size of the
     * tables created.
     *
     * @param path Database directory or stub file, not null
     * @param flags Bitwise-or of action, backend and opening flags
     * @param bytes Block size in bytes, a power of two from 2048 to 65536;
     *  any other size means the default
     * @return Writable database
     */
    public static WritableDatabase openWritable(
        final Path path, final int flags, final int bytes
    ) {
        return DatabaseFactory.writable(
            path, VidrioOptions.of(flags).withBlockSize(bytes)
        );
    }

    /**
     * Opens a read-only database at a path: a vidrio directory or a stub
     * file.
     *
     * @param path Database directory or stub file, not null
     * @return Database at its latest revision
     */
    public static Database open(final Path path) {
        Objects.requireNonNull(path, "path");
        if (!Files.exists(path)) {
            throw new DatabaseNotFoundError(
                String.format("Couldn't stat '%s'", path)
            );
        }
        final Shard shard;
        if (Files.isRegularFile(path)) {
            shard = DatabaseFactory.stub(path, false, VidrioOptions.of(0));
        } else if (Files.isRegularFile(path.resolve(VidrioFormat.versionFileName()))) {
            shard = VidrioShard.open(path);
        } else {
            throw new DatabaseNotFoundError("Couldn't detect type of database");
        }
        return new ShardDatabase(shard);
    }

    private static WritableDatabase writable(
        final Path path, final VidrioOptions options
    ) {
        Objects.requireNonNull(path, "path");
        final Backend backend = Backend.of(options.flags()).orElse(Backend.VIDRIO);
        final Shard shard;
        if (backend == Backend.IN_MEMORY) {
            shard = new InMemoryShard();
        } else if (backend == Backend.STUB
            || Backend.of(options.flags()).isEmpty() && Files.isRegularFile(path)) {
            shard = DatabaseFactory.stub(path, true, options);
        } else {
            shard = VidrioWritableShard.open(path, options);
        }
        return new ShardDatabase(shard);
    }

    private static Shard stub(
        final Path path, final boolean writable, final VidrioOptions options
    ) {
        final List<StubEntry> entries = new StubFile(path).entries();
        if (entries.size() > 1) {
            throw new UnimplementedError("Combining databases is not supported");
        }
        if (entries.isEmpty() && writable) {
            throw new DatabaseNotFoundError(
                String.format("%s: No databases listed", path)
            );
        }
        final Shard shard;
        if (entries.isEmpty()) {
            shard = new EmptyShard();
        } else {
            shard = DatabaseFactory.entry(entries.get(0), writable, options);
        }
        return shard;
    }

    private static Shard entry(
        final StubEntry entry, final boolean writable, final VidrioOptions options
    ) {
        if (!(entry == StubMemory.INSTANCE || entry instanceof StubLocal)) {
            throw new UnimplementedError("Remote databases are not supported");
        }
        final Shard shard;
        if (entry instanceof StubLocal local) {
            if (writable) {
                shard = VidrioWritableShard.open(local.path(), options);
            } else {
                shard = VidrioShard.open(local.path());
            }
        } else {
            shard = new InMemoryShard();
        }
        return shard;
    }
}
