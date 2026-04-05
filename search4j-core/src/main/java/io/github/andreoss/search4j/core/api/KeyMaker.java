/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.DocId;

/**
 * A key maker: extracts a collapse key from a document.
 *
 * <p>A key maker is used for collapsing: documents with the same key
 * are grouped and only the highest-weighted document from each group
 * is returned.</p>
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface KeyMaker {

    /**
     * Extracts the collapse key from a document.
     *
     * @param docid Document identifier
     * @return Key, empty when the document has no key
     */
    String key(DocId docid);
}
