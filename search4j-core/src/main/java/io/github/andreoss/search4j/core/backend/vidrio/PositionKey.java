/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file and the posting lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.types.DocId;

/**
 * A term in a document, naming one position list.
 *
 * @param doc Document
 * @param term Term text
 * @since 0.1.0
 */
public record PositionKey(DocId doc, String term) {
}
