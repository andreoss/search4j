/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Posting list operators combining the streams of a query tree.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.api.Document;

/**
 * Decides whether a matching document is accepted by a query.
 *
 * <p>The reference exposes this as a callable base class; a functional
 * interface keeps the same surface without inheritance.</p>
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface MatchDecider {

    /**
     * Decides whether to accept a document.
     *
     * @param document Candidate document
     * @return True when the document should be considered further
     */
    boolean accept(Document document);
}
