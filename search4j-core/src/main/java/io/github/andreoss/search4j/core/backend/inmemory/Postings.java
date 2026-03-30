/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.backend.TermFrequencies;
import io.github.andreoss.search4j.core.types.DocId;

/**
 * Postings of a term as opened: a cursor and the frequencies at that time.
 *
 * @param cursor Cursor over the postings
 * @param freqs Frequencies when opened
 * @since 0.1.0
 */
record Postings(Seek<DocId, Occurrence> cursor, TermFrequencies freqs) {
}
