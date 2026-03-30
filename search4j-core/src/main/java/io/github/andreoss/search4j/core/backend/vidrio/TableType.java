/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk table format definitions of the vidrio backend.
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
    POSTLIST("postlist", TableLaziness.EAGER),

    /**
     * Document data, keyed by document number.
     */
    DOCDATA("docdata", TableLaziness.LAZY),

    /**
     * Term lists, keyed by document number.
     */
    TERMLIST("termlist", TableLaziness.CHOSEN),

    /**
     * Positional data, keyed by document number and term.
     */
    POSITION("position", TableLaziness.LAZY),

    /**
     * Spelling data.
     */
    SPELLING("spelling", TableLaziness.LAZY),

    /**
     * Synonym data.
     */
    SYNONYM("synonym", TableLaziness.LAZY);

    /**
     * Base name of the table.
     */
    private final String base;

    /**
     * When the table file is created.
     */
    private final TableLaziness creation;

    /**
     * Creates a table type.
     *
     * @param base Base name of the table
     * @param creation When the table file is created
     */
    TableType(final String base, final TableLaziness creation) {
        this.base = base;
        this.creation = creation;
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
     * Name of the table file inside the database directory.
     *
     * @return Base name, a dot and the format extension
     */
    public String fileName() {
        return String.join(".", this.base, VidrioFormat.extensionText());
    }
}
