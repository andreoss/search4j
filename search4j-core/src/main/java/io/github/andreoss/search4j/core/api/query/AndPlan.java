/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.types.QueryOp;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An intersection context: the members merged from nested and-like
 * operators, the excluded union, the positional filters over ranges of the
 * members and the optional union adding weight.
 *
 * @param members Plans of the members
 * @param all Whether an unweighted match-all member was dropped
 * @param excluded Union of the right sides of the differences
 * @param windows Positional filters
 * @param optional Union of the right sides of the and-maybes
 * @since 0.1.0
 */
record AndPlan(List<Plan> members, boolean all, OrPlan excluded,
    List<AndPlan.Window> windows, OrPlan optional) implements Plan {

    /**
     * Creates the plan.
     *
     * @param members Plans of the members
     * @param all Whether an unweighted match-all member was dropped
     * @param excluded Union of the right sides of the differences
     * @param windows Positional filters
     * @param optional Union of the right sides of the and-maybes
     */
    AndPlan {
        members = List.copyOf(members);
        Objects.requireNonNull(excluded, "excluded");
        windows = List.copyOf(windows);
        Objects.requireNonNull(optional, "optional");
    }

    @Override
    public String describe() {
        final String text;
        if (this.members.isEmpty() && !this.all) {
            text = NothingPlan.INSTANCE.describe();
        } else {
            final StringBuilder extra = new StringBuilder(32);
            if (!this.excluded.empty()) {
                extra.append(" NOT ").append(this.excluded.describe());
            }
            for (final AndPlan.Window window : this.windows) {
                extra.append(window.describe());
            }
            if (!this.optional.empty()) {
                extra.append(" MAYBE ").append(this.optional.describe());
            }
            if (extra.isEmpty()) {
                text = this.base();
            } else {
                text = String.format("(%s%s)", this.base(), extra);
            }
        }
        return text;
    }

    private String base() {
        final String text;
        if (this.members.isEmpty()) {
            text = "<alldocuments>:0";
        } else if (this.members.size() == 1) {
            text = this.members.get(0).describe();
        } else {
            text = this.members.stream()
                .map(Plan::describe)
                .collect(Collectors.joining(", ", "AND(", ")"));
        }
        return text;
    }

    /**
     * A positional filter over a range of the members.
     *
     * @param type Either {@link QueryOp#NEAR} or {@link QueryOp#PHRASE}
     * @param begin First member of the range
     * @param end Member after the range
     * @param size Window size
     * @since 0.1.0
     */
    record Window(QueryOp type, int begin, int end, int size) {

        /**
         * Description of the filter.
         *
         * @return Description text
         */
        String describe() {
            return String.format(
                " %s %d-%d window %d", this.type, this.begin, this.end, this.size
            );
        }
    }
}
