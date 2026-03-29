/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.api.Document;
import io.github.andreoss.search4j.core.api.TermIterator;
import io.github.andreoss.search4j.core.api.ValueIterator;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Editable document, built standalone or copied out of a database.
 *
 * <p>Iterators opened on the document read a snapshot, unaffected by later
 * edits.</p>
 *
 * @since 0.1.0
 */
public final class InMemoryDocument implements Document {

    /**
     * The values by slot.
     */
    private final NavigableMap<ValueNo, String> slots;

    /**
     * The term tallies, ordered by {@link TermOrder}.
     */
    private final NavigableMap<String, Tally> terms;

    /**
     * The opaque data.
     */
    private String text;

    /**
     * Creates an empty document.
     */
    public InMemoryDocument() {
        this.slots = new TreeMap<>(Orders.SLOTS);
        this.terms = new TreeMap<>(TermOrder.INSTANCE);
        this.text = "";
    }

    @Override
    public String data() {
        return this.text;
    }

    @Override
    public void setData(final String data) {
        this.text = Objects.requireNonNull(data, "data");
    }

    @Override
    public Optional<String> value(final ValueNo slot) {
        return Optional.ofNullable(this.slots.get(slot));
    }

    @Override
    public void addValue(final ValueNo slot, final String value) {
        if (value.isEmpty()) {
            this.slots.remove(slot);
        } else {
            this.slots.put(slot, value);
        }
    }

    @Override
    public void removeValue(final ValueNo slot) {
        this.slots.remove(slot);
    }

    @Override
    public void clearValues() {
        this.slots.clear();
    }

    @Override
    public TermCount valueCount() {
        return new TermCount(this.slots.size());
    }

    @Override
    public ValueIterator values() {
        return new SlotCursor(new TreeMap<>(this.slots));
    }

    @Override
    public void addTerm(final String term, final TermCount increment) {
        this.tally(term).increase(increment);
    }

    @Override
    public void addPosting(
        final String term,
        final TermPos position,
        final TermCount increment
    ) {
        final Tally tally = this.tally(term);
        tally.increase(increment);
        tally.place(position);
    }

    @Override
    public void removeTerm(final String term) {
        if (this.terms.remove(InMemoryDocument.named(term)) == null) {
            throw new InvalidArgumentError(
                String.format("Term '%s' not present", term)
            );
        }
    }

    @Override
    public void removePosting(
        final String term,
        final TermPos position,
        final TermCount decrement
    ) {
        final Tally tally = this.terms.get(InMemoryDocument.named(term));
        if (tally == null) {
            throw new InvalidArgumentError(
                String.format("Term '%s' not present", term)
            );
        }
        if (!tally.lift(position, decrement)) {
            throw new InvalidArgumentError(
                String.format(
                    "Term '%s' not present at position %s", term, position
                )
            );
        }
        if (tally.spent()) {
            this.terms.remove(term);
        }
    }

    @Override
    public void clearTerms() {
        this.terms.clear();
    }

    @Override
    public TermCount termCount() {
        return new TermCount(this.terms.size());
    }

    @Override
    public TermIterator termList() {
        final NavigableMap<String, Occurrence> frozen =
            new TreeMap<>(TermOrder.INSTANCE);
        for (final Map.Entry<String, Tally> entry : this.terms.entrySet()) {
            frozen.put(entry.getKey(), entry.getValue().freeze());
        }
        return new TermCursor(frozen, Detached.INSTANCE);
    }

    private Tally tally(final String term) {
        return this.terms.computeIfAbsent(
            InMemoryDocument.named(term), key -> new Tally()
        );
    }

    private static String named(final String term) {
        if (term.isEmpty()) {
            throw new InvalidArgumentError("Empty terms are invalid");
        }
        return term;
    }
}
