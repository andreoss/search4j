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
 * Tables of a vidrio database, in on-disk order.
 *
 * @since 0.1.0
 */
public enum TableType {

    /**
     * Posting lists, keyed by term.
     */
    POSTLIST("postlist", TableLaziness.EAGER, 0),

    /**
     * Document data, keyed by document number.
     */
    DOCDATA("docdata", TableLaziness.LAZY, TableType.COMPRESS_MIN),

    /**
     * Term lists, keyed by document number.
     */
    TERMLIST("termlist", TableLaziness.CHOSEN, TableType.COMPRESS_MIN),

    /**
     * Positional data, keyed by document number and term.
     */
    POSITION("position", TableLaziness.LAZY, 0),

    /**
     * Spelling data.
     */
    SPELLING("spelling", TableLaziness.LAZY, TableType.COMPRESS_MIN),

    /**
     * Synonym data.
     */
    SYNONYM("synonym", TableLaziness.LAZY, TableType.COMPRESS_MIN);

    /**
     * Compression threshold of the tables whose tags are compressed:
     * only tags longer than this many bytes are compressed.
     */
    public static final int COMPRESS_MIN = 18;

    /**
     * Base name of the table.
     */
    private final String base;

    /**
     * When the table file is created.
     */
    private final TableLaziness creation;

    /**
     * Smallest tag length compressed, 0 for no compression.
     */
    private final int compress;

    /**
     * Creates a table type.
     *
     * @param base Base name of the table
     * @param creation When the table file is created
     * @param compress Smallest tag length compressed, 0 for none
     */
    TableType(
        final String base, final TableLaziness creation, final int compress
    ) {
        this.base = base;
        this.creation = creation;
        this.compress = compress;
    }

    /**
     * Base name of the table.
     *
     * @return Lower-case name
     */
    public String tableName() {
        return this.base;
    }

    /**
     * When the table file is created.
     *
     * @return Creation policy
     */
    public TableLaziness laziness() {
        return this.creation;
    }

    /**
     * Compression threshold of a new table of this type.
     *
     * @return Smallest tag length compressed, 0 for no compression
     */
    public int compression() {
        return this.compress;
    }

    /**
     * Name of the table file inside the database directory.
     *
     * @return Base name, a dot and the format extension
     */
    public String fileName() {
        return String.join(".", this.base, VidrioFormat.extensionText());
    }
}
