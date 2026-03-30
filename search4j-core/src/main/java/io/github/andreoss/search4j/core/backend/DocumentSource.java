/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.backend.list.TermList;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.NavigableMap;
import java.util.Optional;

/**
 * Port through which a document held by a shard reads its parts on demand.
 *
 * @since 0.1.0
 */
public interface DocumentSource {

    /**
     * Reads the opaque data.
     *
     * @return Data, empty when never set
     */
    String data();

    /**
     * Reads every value.
     *
     * @return Values by slot, a fresh map the caller may change
     */
    NavigableMap<ValueNo, String> values();

    /**
     * Reads one value.
     *
     * @param slot Slot to read
     * @return Value, empty when the slot has none
     */
    Optional<String> value(ValueNo slot);

    /**
     * Opens the terms.
     *
     * @return Terms with frequencies and positions
     */
    TermList terms();

    /**
     * Tests whether the shard holds any positions.
     *
     * @return True when positions may be present
     */
    boolean positional();
}
