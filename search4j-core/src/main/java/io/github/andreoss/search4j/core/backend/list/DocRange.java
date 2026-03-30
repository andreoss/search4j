/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Iterator bases shared by the database backends.
 */
package io.github.andreoss.search4j.core.backend.list;

import io.github.andreoss.search4j.core.types.DocId;

/**
 * Inclusive range of document identifiers a posting list may return.
 *
 * @param first Lowest identifier possible
 * @param last Highest identifier possible
 * @since 0.1.0
 */
public record DocRange(DocId first, DocId last) {
}
