/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.backend.list.ValueList;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;

/**
 * Value list over the slots of one document.
 *
 * <p>Document identifiers have no meaning here and raise an
 * invalid-operation error.</p>
 *
 * @since 0.1.0
 */
final class DocumentValueList implements ValueList {

    /**
     * The values by slot.
     */
    private final NavigableMap<ValueNo, String> values;

    /**
     * The current entry, absent before the start and at the end.
     */
    private Optional<Map.Entry<ValueNo, String>> current;

    /**
     * Whether a move was made.
     */
    private boolean started;

    /**
     * Creates a list over values.
     *
     * @param values The values by slot
     */
    DocumentValueList(final NavigableMap<ValueNo, String> values) {
        this(values, Optional.empty());
    }

    /**
     * Creates a list on an entry.
     *
     * @param values The entries
     * @param current The current entry, absent before the start
     */
    private DocumentValueList(
        final NavigableMap<ValueNo, String> values,
        final Optional<Map.Entry<ValueNo, String>> current
    ) {
        this.values = values;
        this.current = current;
        this.started = false;
    }

    @Override
    public DocId docId() {
        throw new InvalidOperationError(
            "Document identifier is not meaningful for the values of a document"
        );
    }

    @Override
    public String value() {
        return this.entry().getValue();
    }

    @Override
    public ValueNo slot() {
        return this.entry().getKey();
    }

    @Override
    public boolean atEnd() {
        return this.started && this.current.isEmpty();
    }

    @Override
    public void next() {
        if (this.started) {
            this.current = this.current.map(
                entry -> this.values.higherEntry(entry.getKey())
            );
        } else {
            this.started = true;
            this.current = Optional.ofNullable(this.values.firstEntry());
        }
    }

    @Override
    public void skipTo(final DocId target) {
        throw new InvalidOperationError(
            "Skipping by document is not meaningful for the values of a document"
        );
    }

    @Override
    public String description() {
        return this.current.map(
            entry -> String.format(
                "DocumentValueList(slot=%s, value=\"%s\")",
                entry.getKey(), entry.getValue()
            )
        ).orElse("DocumentValueList(atend)");
    }

    private Map.Entry<ValueNo, String> entry() {
        return this.current.orElseThrow(
            () -> new InvalidOperationError("Value list is not on a value")
        );
    }
}
