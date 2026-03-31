/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file and the posting lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.types.TermCount;

/**
 * A change of the frequency of a term in a document.
 *
 * @param old Frequency the document had
 * @param updated New frequency
 * @since 0.1.0
 */
public record WdfChange(TermCount old, TermCount updated) {
}
