/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.types.QueryOp;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The flattening step of the reference, run when a query becomes posting
 * lists: nested intersections, filters, differences, and-maybes and
 * positional operators merge into one intersection context, nested unions
 * and elite sets into one union context, nested exclusive unions into one,
 * the right sides of differences into one unweighted union and those of
 * and-maybes into one weighted union; scale factors multiply down.
 *
 * <p>Construction keeps the tree as built; this step is explicit. What
 * depends on a database (empty posting lists, zero maximum weights, the
 * choice of the elite members, positions being stored) is left to the
 * matcher.</p>
 *
 * @since 0.1.0
 */
final class Planner {

    /**
     * Operators merged into an intersection context.
     */
    private static final Set<QueryOp> MERGED = EnumSet.of(
        QueryOp.AND, QueryOp.FILTER, QueryOp.AND_NOT, QueryOp.AND_MAYBE,
        QueryOp.NEAR, QueryOp.PHRASE
    );

    /**
     * Whether the posting lists opened must carry positions.
     */
    private boolean positions;

    /**
     * Creates a planner outside any positional operator.
     */
    private Planner() {
        this.positions = false;
    }

    /**
     * Flattens a query.
     *
     * @param query The query
     * @param factor Weight factor, zero when unweighted
     * @return The plan, {@link NothingPlan} for a match-nothing query
     */
    static Plan plan(final Query query, final double factor) {
        final Plan plan;
        if (query.empty()) {
            plan = NothingPlan.INSTANCE;
        } else {
            plan = new Planner().unit(query.node(), factor);
        }
        return plan;
    }

    private Plan unit(final Node node, final double factor) {
        final Plan plan;
        if (node instanceof ScaleNode scale) {
            plan = this.unit(scale.scaled().node(), factor * scale.factor());
        } else if (node instanceof BranchNode branch) {
            plan = this.branch(branch, factor);
        } else {
            plan = new LeafPlan(node, factor);
        }
        return plan;
    }

    private Plan branch(final BranchNode node, final double factor) {
        final Plan plan;
        final QueryOp type = node.type();
        if (factor == 0.0 && Planner.unionlike(type)) {
            final OrDraft union = new OrDraft(OrPlan.Kind.BOOL_OR);
            this.unweighted(node.subqueries(), union);
            plan = union.plan();
        } else if (type == QueryOp.SYNONYM) {
            final OrDraft union = new OrDraft(OrPlan.Kind.BOOL_OR);
            this.unweighted(node.subqueries(), union);
            plan = new SynonymPlan(factor, union.plan());
        } else if (type == QueryOp.MAX) {
            final OrDraft union = new OrDraft(OrPlan.Kind.MAX);
            this.weighted(node.subqueries(), factor, union);
            plan = union.plan();
        } else if (type == QueryOp.OR || type == QueryOp.ELITE_SET) {
            final OrDraft union = new OrDraft(OrPlan.Kind.OR);
            this.orlike(node, factor, union);
            plan = union.plan();
        } else if (type == QueryOp.XOR) {
            final OrDraft union = new OrDraft(OrPlan.Kind.XOR);
            this.xorlike(node, factor, union);
            plan = union.plan();
        } else {
            final AndDraft meet = new AndDraft();
            this.andlike(node, factor, meet);
            plan = meet.plan();
        }
        return plan;
    }

    private void andlike(final Node node, final double factor,
        final AndDraft meet) {
        if (node instanceof ScaleNode scale) {
            this.andlike(scale.scaled().node(), factor * scale.factor(), meet);
        } else if (node instanceof BranchNode branch && Planner.merges(branch.type())) {
            this.merge(branch, factor, meet);
        } else if (
            node.type() == QueryOp.LEAF_MATCH_ALL && !this.positions && factor == 0.0
        ) {
            meet.everything();
        } else {
            meet.add(this.unit(node, factor));
        }
    }

    private void merge(final BranchNode node, final double factor,
        final AndDraft meet) {
        final List<Query> subs = node.subqueries();
        switch (node.type()) {
            case AND -> subs.forEach(sub -> this.andlike(sub.node(), factor, meet));
            case FILTER -> {
                this.andlike(subs.get(0).node(), factor, meet);
                subs.subList(1, subs.size())
                    .forEach(sub -> this.andlike(sub.node(), 0.0, meet));
            }
            case AND_NOT -> {
                this.andlike(subs.get(0).node(), factor, meet);
                this.unweighted(subs.subList(1, subs.size()), meet.excluded());
            }
            case AND_MAYBE -> {
                this.andlike(subs.get(0).node(), factor, meet);
                if (factor != 0.0) {
                    this.weighted(subs.subList(1, subs.size()), factor, meet.optional());
                }
            }
            default -> this.windowed(node, factor, meet);
        }
    }

    private void windowed(final BranchNode node, final double factor,
        final AndDraft meet) {
        final boolean saved = this.positions;
        this.positions = true;
        for (final Query sub : node.subqueries()) {
            final Plan plan = this.unit(sub.node(), factor);
            if (sub.type() == QueryOp.LEAF_TERM) {
                meet.add(plan);
            } else {
                meet.add(new PositionsPlan(plan));
            }
        }
        meet.window(node.type(), node.subqueryCount(), node.parameter());
        this.positions = saved;
    }

    private void orlike(final Node node, final double factor,
        final OrDraft union) {
        if (node instanceof BranchNode branch && branch.type() == QueryOp.OR) {
            this.weighted(branch.subqueries(), factor, union);
        } else if (node instanceof BranchNode branch
            && branch.type() == QueryOp.ELITE_SET) {
            final int begin = union.size();
            this.weighted(branch.subqueries(), factor, union);
            union.elite(branch.parameter(), begin);
        } else {
            union.add(this.unit(node, factor));
        }
    }

    private void weighted(final List<Query> subs, final double factor,
        final OrDraft union) {
        subs.forEach(sub -> this.orlike(sub.node(), factor, union));
    }

    private void unweighted(final List<Query> subs, final OrDraft union) {
        for (final Query sub : subs) {
            if (sub.type() == QueryOp.OR) {
                this.unweighted(((BranchNode) sub.node()).subqueries(), union);
            } else {
                union.add(this.unit(sub.node(), 0.0));
            }
        }
    }

    private void xorlike(final Node node, final double factor,
        final OrDraft union) {
        if (node instanceof BranchNode branch && branch.type() == QueryOp.XOR) {
            branch.subqueries().forEach(
                sub -> this.xorlike(sub.node(), factor, union)
            );
        } else {
            union.add(this.unit(node, factor));
        }
    }

    private static boolean unionlike(final QueryOp type) {
        return type == QueryOp.OR || type == QueryOp.SYNONYM || type == QueryOp.MAX;
    }

    private static boolean merges(final QueryOp type) {
        return Planner.MERGED.contains(type);
    }
}
