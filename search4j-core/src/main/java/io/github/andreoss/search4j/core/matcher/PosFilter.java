/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Matcher core of the search engine.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.List;

/**
 * Positional check applied above a conjunction to the posting lists of a
 * phrase or near subquery.
 *
 * @param type Phrase or near
 * @param begin First posting list of the subquery in the conjunction
 * @param end Posting list after the last one of the subquery
 * @param window Window of positions
 * @since 0.10.0
 */
record PosFilter(QueryOp type, int begin, int end, TermPos window) {

    EstimateKind kind() {
        final EstimateKind result;
        if (this.type == QueryOp.NEAR) {
            result = EstimateKind.NEAR;
        } else if (this.window.value() == this.end - this.begin) {
            result = EstimateKind.EXACT_PHRASE;
        } else {
            result = EstimateKind.PHRASE;
        }
        return result;
    }

    MatchTree apply(final MatchTree tree, final List<PostList> lists, final Matching env) {
        final EstimateKind kind = this.kind();
        final SelectSetup setup = SelectSetup.simple(
            tree.list(), lists.subList(this.begin, this.end), kind.divisor()
        ).weighed(env::docStats);
        final SelectPostList select;
        if (kind == EstimateKind.NEAR) {
            select = new NearPostList(SelectSetup.positional(setup, this.window));
        } else if (kind == EstimateKind.PHRASE) {
            select = new PhrasePostList(SelectSetup.positional(setup, this.window));
        } else {
            select = new ExactPhrasePostList(setup);
        }
        return new MatchTree(
            select, MatchEstimate.filtered(kind, tree.estimate(), select::ratio)
        );
    }
}
