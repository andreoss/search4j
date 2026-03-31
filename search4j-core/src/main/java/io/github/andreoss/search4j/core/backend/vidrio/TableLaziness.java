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
 * When a table file is created on disk.
 *
 * @since 0.1.0
 */
public enum TableLaziness {

    /**
     * Created with the database.
     */
    EAGER,

    /**
     * Created on its first write.
     */
    LAZY,

    /**
     * Eager or lazy as chosen when the database is opened.
     */
    CHOSEN
}
