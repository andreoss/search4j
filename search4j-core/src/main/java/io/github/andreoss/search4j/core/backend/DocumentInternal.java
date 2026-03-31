/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.api.Document;
import io.github.andreoss.search4j.core.api.TermIterator;
import io.github.andreoss.search4j.core.api.ValueIterator;
import io.github.andreoss.search4j.core.backend.list.EmptyTermList;
import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.TermList;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * Editable document, standalone or read lazily from a shard.
 *
 * <p>Data, values and terms are fetched from the source on first need and
 * cached; once edited, the cached copy is the document. Edits never reach the
 * shard. Iterators read a snapshot.</p>
 *
 * @since 0.1.0
 */
public final class DocumentInternal implements Document {

    /**
     * The document identifier, {@link DocId#NONE} when standalone.
     */
    private final DocId docid;

    /**
     * The source of the unedited parts, empty when standalone.
     */
    private final Optional<DocumentSource> source;

    /**
     * The edited data, null while unedited.
     */
    private String text;

    /**
     * The fetched or edited values, null until needed.
     */
    private NavigableMap<ValueNo, String> slots;

    /**
     * The fetched or edited terms, null until needed.
     */
    private NavigableMap<String, TermEntry> terms;

    /**
     * The number of live terms in {@link #terms}.
     */
    private int size;

    /**
     * Whether positions may differ from those stored.
     */
    private boolean moved;

    /**
     * Creates an empty standalone document.
     */
    public DocumentInternal() {
        this(DocId.NONE, Optional.empty());
    }

    /**
     * Creates a document read lazily from a shard.
     *
     * @param docid Identifier in the shard
     * @param source Source of the stored parts
     */
    public DocumentInternal(final DocId docid, final DocumentSource source) {
        this(docid, Optional.of(source));
    }

    /**
     * Creates a document.
     *
     * @param docid Identifier in the shard
     * @param source Source, empty when standalone
     */
    private DocumentInternal(
        final DocId docid,
        final Optional<DocumentSource> source
    ) {
        this.docid = docid;
        this.source = source;
        this.size = 0;
        this.moved = false;
    }

    /**
     * Identifier in the shard.
     *
     * @return Identifier, {@link DocId#NONE} when standalone
     */
    public DocId docId() {
        return this.docid;
    }

    /**
     * Tests whether the data was edited.
     *
     * @return True once set
     */
    public boolean dataModified() {
        return this.text != null;
    }

    /**
     * Tests whether the terms were fetched or edited.
     *
     * @return True once term edits may have happened
     */
    public boolean termsModified() {
        return this.terms != null;
    }

    /**
     * Tests whether the values were fetched or edited.
     *
     * @return True once value edits may have happened
     */
    public boolean valuesModified() {
        return this.slots != null;
    }

    /**
     * Tests whether positions may differ from those stored.
     *
     * @return True after a position edit or a removal of positioned terms
     */
    public boolean positionsModified() {
        return this.moved;
    }

    /**
     * Opens the current terms, cached or stored.
     *
     * @return Live list over the terms
     */
    public TermList openTermList() {
        final TermList result;
        if (this.terms == null) {
            result = this.source.map(DocumentSource::terms)
                .orElse(EmptyTermList.INSTANCE);
        } else {
            result = new DocumentTermList(this.terms);
        }
        return result;
    }

    /**
     * Human-readable description for diagnostics.
     *
     * @return Description with the identifier and the cached parts
     */
    public String description() {
        final StringJoiner desc = new StringJoiner(", ", "Document(", ")");
        desc.add(String.format("docid=%s", this.docid));
        if (this.text != null) {
            desc.add(String.format("data=%s", this.text));
        }
        if (this.terms != null) {
            desc.add(String.format("terms[%d]", this.terms.size()));
        }
        if (this.slots != null) {
            desc.add(String.format("values[%d]", this.slots.size()));
        }
        return desc.toString();
    }

    @Override
    public String data() {
        String result = this.text;
        if (result == null) {
            result = this.source.map(DocumentSource::data).orElse("");
        }
        return result;
    }

    @Override
    public void setData(final String data) {
        this.text = Objects.requireNonNull(data, "data");
    }

    @Override
    public Optional<String> value(final ValueNo slot) {
        final Optional<String> result;
        if (this.slots == null) {
            result = this.source.flatMap(src -> src.value(slot));
        } else {
            result = Optional.ofNullable(this.slots.get(slot));
        }
        return result;
    }

    @Override
    public void addValue(final ValueNo slot, final String value) {
        this.fetchValues();
        if (value.isEmpty()) {
            this.slots.remove(slot);
        } else {
            this.slots.put(slot, value);
        }
    }

    @Override
    public void removeValue(final ValueNo slot) {
        this.addValue(slot, "");
    }

    @Override
    public void clearValues() {
        if (this.slots != null) {
            this.slots.clear();
        } else if (this.source.isPresent()) {
            this.slots = new TreeMap<>();
        }
    }

    @Override
    public TermCount valueCount() {
        this.fetchValues();
        return new TermCount(this.slots.size());
    }

    @Override
    public ValueIterator values() {
        this.fetchValues();
        return new ValueAdapter(new DocumentValueList(new TreeMap<>(this.slots)));
    }

    @Override
    public void addTerm(final String term, final TermCount increment) {
        this.fetchTerms();
        final TermEntry entry = this.terms.get(DocumentInternal.named(term));
        if (entry == null) {
            this.terms.put(term, new TermEntry(increment, List.of()));
            this.size += 1;
        } else if (entry.increase(increment)) {
            this.size += 1;
        }
    }

    @Override
    public void addPosting(
        final String term,
        final TermPos position,
        final TermCount increment
    ) {
        this.fetchTerms();
        this.moved = true;
        final TermEntry entry = this.terms.get(DocumentInternal.named(term));
        if (entry == null) {
            this.terms.put(term, new TermEntry(increment, List.of(position)));
            this.size += 1;
        } else if (entry.place(increment, position)) {
            this.size += 1;
        }
    }

    @Override
    public void removeTerm(final String term) {
        this.fetchTerms();
        final TermEntry entry = this.terms.get(DocumentInternal.named(term));
        if (entry == null || entry.deleted()) {
            throw new InvalidArgumentError(
                String.format("Term '%s' not present", term)
            );
        }
        if (entry.positioned()) {
            this.moved = true;
        }
        entry.remove();
        this.size -= 1;
    }

    @Override
    public void removePosting(
        final String term,
        final TermPos position,
        final TermCount decrement
    ) {
        this.fetchTerms();
        final TermEntry entry = this.terms.get(DocumentInternal.named(term));
        if (entry == null || entry.deleted()) {
            throw new InvalidArgumentError(
                String.format("Term '%s' not present", term)
            );
        }
        if (!entry.unplace(position)) {
            throw new InvalidArgumentError(
                String.format(
                    "Term '%s' not present at position %s", term, position
                )
            );
        }
        if (entry.decrease(decrement)) {
            this.size -= 1;
        }
        this.moved = true;
    }

    @Override
    public void clearTerms() {
        if (this.terms != null) {
            this.terms.clear();
            this.size = 0;
        } else if (this.source.isPresent()) {
            this.terms = new TreeMap<>(TermOrder.INSTANCE);
            this.size = 0;
        }
        this.moved = this.source.map(DocumentSource::positional)
            .orElse(false);
    }

    @Override
    public TermCount termCount() {
        final TermCount result;
        if (this.terms == null) {
            result = this.source.map(src -> src.terms().approxSize())
                .orElse(TermCount.ZERO);
        } else {
            result = new TermCount(this.size);
        }
        return result;
    }

    @Override
    public TermIterator termList() {
        this.fetchTerms();
        final NavigableMap<String, TermEntry> frozen =
            new TreeMap<>(TermOrder.INSTANCE);
        for (final Map.Entry<String, TermEntry> entry
            : this.terms.entrySet()) {
            frozen.put(entry.getKey(), entry.getValue().copy());
        }
        return new TermAdapter(new DocumentTermList(frozen));
    }

    private void fetchValues() {
        if (this.slots == null) {
            this.slots = this.source.map(DocumentSource::values)
                .orElseGet(TreeMap::new);
        }
    }

    private void fetchTerms() {
        if (this.terms == null) {
            this.terms = new TreeMap<>(TermOrder.INSTANCE);
            this.size = 0;
            TermList list = this.source.map(DocumentSource::terms)
                .orElse(EmptyTermList.INSTANCE).next();
            while (!list.atEnd()) {
                this.terms.put(
                    list.term(),
                    new TermEntry(list.wdf(), DocumentInternal.positions(list))
                );
                this.size += 1;
                list = list.next();
            }
        }
    }

    private static List<TermPos> positions(final TermList list) {
        final List<TermPos> result = new ArrayList<>(0);
        final PositionList positions = list.positionList();
        while (positions.next()) {
            result.add(positions.position());
        }
        return result;
    }

    private static String named(final String term) {
        if (term.isEmpty()) {
            throw new InvalidArgumentError("Empty terms are invalid");
        }
        return term;
    }
}
