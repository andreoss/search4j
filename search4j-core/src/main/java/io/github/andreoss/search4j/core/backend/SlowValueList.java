/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.backend.list.ValueList;
import io.github.andreoss.search4j.core.error.DocNotFoundError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.Optional;

/**
 * Value list of one slot built by opening every document of a shard in turn,
 * for backends without a value stream of their own.
 *
 * @since 0.1.0
 */
public final class SlowValueList implements ValueList {

    /**
     * The shard read.
     */
    private final Shard shard;

    /**
     * The slot read.
     */
    private final ValueNo number;

    /**
     * The last identifier to read, {@link DocId#NONE} once at the end.
     */
    private DocId last;

    /**
     * The current document.
     */
    private DocId current;

    /**
     * The current value, empty when the current document has none.
     */
    private String text;

    /**
     * Creates the list.
     *
     * @param shard Shard read
     * @param slot Slot read
     */
    public SlowValueList(final Shard shard, final ValueNo slot) {
        this(shard, slot, shard.lastDocId());
    }

    /**
     * Creates the list up to a last identifier.
     *
     * @param shard Shard read
     * @param slot Slot read
     * @param last Last identifier to read
     */
    private SlowValueList(
        final Shard shard,
        final ValueNo slot,
        final DocId last
    ) {
        this.shard = shard;
        this.number = slot;
        this.last = last;
        this.current = DocId.NONE;
        this.text = "";
    }

    @Override
    public DocId docId() {
        return this.current;
    }

    @Override
    public String value() {
        return this.text;
    }

    @Override
    public ValueNo slot() {
        return this.number;
    }

    @Override
    public boolean atEnd() {
        return !this.last.valid();
    }

    @Override
    public void next() {
        boolean found = false;
        while (!found && this.current.compareTo(this.last) < 0) {
            this.current = this.current.next();
            final Optional<String> value = this.read(this.current);
            if (value.isPresent()) {
                this.text = value.get();
                found = true;
            }
        }
        if (!found) {
            this.last = DocId.NONE;
        }
    }

    @Override
    public void skipTo(final DocId target) {
        if (target.compareTo(this.current) > 0) {
            this.current = new DocId(target.value() - 1);
            this.next();
        }
    }

    @Override
    public boolean check(final DocId target) {
        final boolean result;
        if (target.compareTo(this.current) <= 0) {
            result = !this.text.isEmpty();
        } else if (target.compareTo(this.last) > 0) {
            this.last = DocId.NONE;
            result = true;
        } else {
            this.current = target;
            final Optional<String> value = this.read(target);
            this.text = value.orElse("");
            result = value.isPresent();
        }
        return result;
    }

    @Override
    public String description() {
        final String result;
        if (this.atEnd()) {
            result = String.format("SlowValueList(slot=%s, atend)", this.number);
        } else {
            result = String.format(
                "SlowValueList(slot=%s, docid=%s, value=\"%s\")",
                this.number, this.current, this.text
            );
        }
        return result;
    }

    private Optional<String> read(final DocId docid) {
        Optional<String> result;
        try {
            result = this.shard.openDocument(docid, true)
                .flatMap(doc -> doc.value(this.number));
        } catch (final DocNotFoundError missing) {
            result = Optional.empty();
        }
        return result;
    }
}
