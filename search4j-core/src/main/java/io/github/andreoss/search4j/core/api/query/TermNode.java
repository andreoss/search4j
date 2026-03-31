/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.unicode.Description;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A term leaf; the empty term matches every document.
 *
 * @param term The term, empty for every document
 * @param wqf Within-query frequency
 * @param pos Query position, zero when none
 * @since 0.1.0
 */
record TermNode(String term, TermCount wqf, TermPos pos) implements Node {

    /**
     * Creates the leaf.
     *
     * @param term The term, empty for every document
     * @param wqf Within-query frequency
     * @param pos Query position, zero when none
     */
    TermNode {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(wqf, "wqf");
        Objects.requireNonNull(pos, "pos");
    }

    @Override
    public QueryOp type() {
        final QueryOp type;
        if (this.term.isEmpty()) {
            type = QueryOp.LEAF_MATCH_ALL;
        } else {
            type = QueryOp.LEAF_TERM;
        }
        return type;
    }

    @Override
    public String describe() {
        final StringBuilder out = new StringBuilder(this.term.length() + 8);
        if (this.term.isEmpty()) {
            out.append("<alldocuments>");
        } else {
            out.append(Description.escape(this.term));
        }
        if (this.wqf.value() != 1) {
            out.append('#').append(this.wqf.value());
        }
        if (this.pos.value() != 0) {
            out.append('@').append(this.pos.value());
        }
        return out.toString();
    }

    @Override
    public TermCount length() {
        return this.wqf;
    }

    @Override
    public Stream<TermAt> terms() {
        final Stream<TermAt> terms;
        if (this.term.isEmpty()) {
            terms = Stream.empty();
        } else {
            terms = Stream.of(new TermAt(this.pos, this.term));
        }
        return terms;
    }
}
