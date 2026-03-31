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

import io.github.andreoss.search4j.core.backend.list.TermList;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.NavigableMap;
import java.util.Optional;

/**
 * Reads of the stored parts of documents, as stored or as changed so far.
 *
 * @since 0.1.0
 */
public interface DocumentFetch {

    /**
     * Data of a document.
     *
     * @param doc Document identifier
     * @return Data, empty when none is stored
     */
    String data(DocId doc);

    /**
     * Every value of a document.
     *
     * @param doc Document identifier
     * @return Values by slot, a fresh map
     */
    NavigableMap<ValueNo, String> values(DocId doc);

    /**
     * One value of a document.
     *
     * @param doc Document identifier
     * @param slot Slot number
     * @return Value, empty when the slot has none
     */
    Optional<String> value(DocId doc, ValueNo slot);

    /**
     * Terms of a document.
     *
     * @param doc Document identifier
     * @return Terms with frequencies and positions
     */
    TermList terms(DocId doc);

    /**
     * Tests whether any document carries positions.
     *
     * @return True when positions may be present
     */
    boolean positional();
}
