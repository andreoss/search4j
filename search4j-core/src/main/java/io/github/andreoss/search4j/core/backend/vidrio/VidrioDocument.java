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

import io.github.andreoss.search4j.core.backend.DocumentSource;
import io.github.andreoss.search4j.core.backend.list.TermList;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.NavigableMap;
import java.util.Optional;

/**
 * Source of a document read lazily from a vidrio database: data, values
 * and terms are fetched when first needed.
 *
 * @param doc Document identifier
 * @param fetch Reads of the stored parts
 * @since 0.1.0
 */
public record VidrioDocument(DocId doc, DocumentFetch fetch)
    implements DocumentSource {

    @Override
    public String data() {
        return this.fetch.data(this.doc);
    }

    @Override
    public NavigableMap<ValueNo, String> values() {
        return this.fetch.values(this.doc);
    }

    @Override
    public Optional<String> value(final ValueNo slot) {
        return this.fetch.value(this.doc, slot);
    }

    @Override
    public TermList terms() {
        return this.fetch.terms(this.doc);
    }

    @Override
    public boolean positional() {
        return this.fetch.positional();
    }
}
