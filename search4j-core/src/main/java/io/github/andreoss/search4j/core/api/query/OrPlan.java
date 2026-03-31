/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A union context: the members merged from nested operators of the same
 * kind, with the elite set selections still to make.
 *
 * @param kind How the members combine
 * @param members Plans of the members
 * @param elites Elite set selections over ranges of the members
 * @since 0.1.0
 */
record OrPlan(OrPlan.Kind kind, List<Plan> members, List<OrPlan.Elite> elites)
    implements Plan {

    /**
     * Creates the plan.
     *
     * @param kind How the members combine
     * @param members Plans of the members
     * @param elites Elite set selections over ranges of the members
     */
    OrPlan {
        Objects.requireNonNull(kind, "kind");
        members = List.copyOf(members);
        elites = List.copyOf(elites);
    }

    /**
     * Tests whether the context has no member.
     *
     * @return True without members
     */
    boolean empty() {
        return this.members.isEmpty();
    }

    @Override
    public String describe() {
        final String text;
        if (this.members.isEmpty()) {
            text = NothingPlan.INSTANCE.describe();
        } else if (this.members.size() == 1 && this.elites.isEmpty()) {
            text = this.members.get(0).describe();
        } else {
            final String union = this.union();
            if (this.elites.isEmpty()) {
                text = union;
            } else {
                text = this.elites.stream()
                    .map(OrPlan.Elite::describe)
                    .collect(Collectors.joining("", "(".concat(union), ")"));
            }
        }
        return text;
    }

    private String union() {
        return this.members.stream()
            .map(Plan::describe)
            .collect(Collectors.joining(", ", this.kind.name().concat("("), ")"));
    }

    /**
     * How the members of a union combine.
     *
     * @since 0.1.0
     */
    enum Kind {

        /**
         * Weighted union.
         */
        OR,

        /**
         * Unweighted union.
         */
        BOOL_OR,

        /**
         * Union weighted by the best member.
         */
        MAX,

        /**
         * Exclusive union.
         */
        XOR
    }

    /**
     * Selection of the best members of a range.
     *
     * @param size Members kept
     * @param begin First member of the range
     * @param end Member after the range
     * @since 0.1.0
     */
    record Elite(int size, int begin, int end) {

        /**
         * Description of the selection.
         *
         * @return Description text
         */
        String describe() {
            return String.format(
                " ELITE %d of %d-%d", this.size, this.begin, this.end
            );
        }
    }
}
