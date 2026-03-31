/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.types.QueryOp;
import java.util.ArrayList;
import java.util.List;

/**
 * An intersection context being gathered by the flattening step.
 *
 * @since 0.1.0
 */
final class AndDraft {

    /**
     * Members gathered so far.
     */
    private final List<Plan> members;

    /**
     * Right sides of differences gathered so far.
     */
    private final OrDraft excluded;

    /**
     * Positional filters gathered so far.
     */
    private final List<AndPlan.Window> windows;

    /**
     * Right sides of and-maybes gathered so far.
     */
    private final OrDraft optional;

    /**
     * Whether an unweighted match-all member was dropped.
     */
    private boolean all;

    /**
     * Creates an empty context.
     */
    AndDraft() {
        this.members = new ArrayList<>(4);
        this.excluded = new OrDraft(OrPlan.Kind.BOOL_OR);
        this.windows = new ArrayList<>(1);
        this.optional = new OrDraft(OrPlan.Kind.OR);
        this.all = false;
    }

    /**
     * Adds a member.
     *
     * @param member Plan of the member
     */
    void add(final Plan member) {
        this.members.add(member);
    }

    /**
     * Notes an unweighted match-all member, which adds no posting list.
     */
    void everything() {
        this.all = true;
    }

    /**
     * Records a positional filter over the last members.
     *
     * @param type Either {@link QueryOp#NEAR} or {@link QueryOp#PHRASE}
     * @param count Number of last members filtered
     * @param size Window size
     */
    void window(final QueryOp type, final int count, final int size) {
        final int end = this.members.size();
        this.windows.add(new AndPlan.Window(type, end - count, end, size));
    }

    /**
     * Union of the right sides of differences.
     *
     * @return The union being gathered
     */
    OrDraft excluded() {
        return this.excluded;
    }

    /**
     * Union of the right sides of and-maybes.
     *
     * @return The union being gathered
     */
    OrDraft optional() {
        return this.optional;
    }

    /**
     * The finished context.
     *
     * @return The plan
     */
    AndPlan plan() {
        return new AndPlan(
            this.members, this.all, this.excluded.plan(), this.windows,
            this.optional.plan()
        );
    }
}
