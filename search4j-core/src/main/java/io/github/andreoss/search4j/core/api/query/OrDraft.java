/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import java.util.ArrayList;
import java.util.List;

/**
 * A union context being gathered by the flattening step.
 *
 * @since 0.1.0
 */
final class OrDraft {

    /**
     * How the members combine.
     */
    private final OrPlan.Kind kind;

    /**
     * Members gathered so far.
     */
    private final List<Plan> members;

    /**
     * Elite set selections gathered so far.
     */
    private final List<OrPlan.Elite> elites;

    /**
     * Creates an empty context.
     *
     * @param kind How the members combine
     */
    OrDraft(final OrPlan.Kind kind) {
        this.kind = kind;
        this.members = new ArrayList<>(4);
        this.elites = new ArrayList<>(1);
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
     * Number of members gathered.
     *
     * @return Member count
     */
    int size() {
        return this.members.size();
    }

    /**
     * Records an elite set selection over the members from an index on,
     * when it keeps fewer than the range holds.
     *
     * @param size Members kept
     * @param begin First member of the range
     */
    void elite(final int size, final int begin) {
        final int end = this.members.size();
        if (Integer.compareUnsigned(size, end - begin) < 0) {
            this.elites.add(new OrPlan.Elite(size, begin, end));
        }
    }

    /**
     * The finished context.
     *
     * @return The plan
     */
    OrPlan plan() {
        return new OrPlan(this.kind, this.members, this.elites);
    }
}
