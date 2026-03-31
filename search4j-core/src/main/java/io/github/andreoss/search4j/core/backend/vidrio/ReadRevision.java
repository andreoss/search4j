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

/**
 * One revision of a database opened to read: its version file, tables and
 * read side.
 *
 * @param version Version file read
 * @param tables Tables of the revision
 * @param reader Read side over the tables
 * @since 0.1.0
 */
public record ReadRevision(
    VersionFile version, ReadTables tables, VidrioReader reader
) {

    /**
     * Opens the latest revision of a database directory.
     *
     * @param files Table files of the directory
     * @return Revision open
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static ReadRevision open(final TableFiles files) {
        final VersionFile version = new VersionFile(files.dir());
        version.read();
        return ReadRevision.over(files, version);
    }

    /**
     * Opens the revision of a version file read.
     *
     * @param files Table files of the directory
     * @param version Version file read
     * @return Revision open
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static ReadRevision over(
        final TableFiles files, final VersionFile version
    ) {
        final ReadTables tables = ReadTables.open(files, version);
        return new ReadRevision(
            version, tables, VidrioReader.over(version, tables)
        );
    }
}
