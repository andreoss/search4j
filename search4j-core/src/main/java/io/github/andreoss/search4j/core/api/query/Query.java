/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.backend.TermOrder;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * An immutable query: a term, a value condition, an expansion, or an
 * operator over subqueries.
 *
 * <p>{@link #MATCH_NOTHING} behaves like {@code false}: it absorbs an
 * intersection and vanishes from a union. The description renders the
 * tree as {@code Query(...)}.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings({"PMD.ProhibitPublicStaticMethods", "PMD.TooManyMethods"})
public final class Query {

    /**
     * The query matching no document.
     */
    public static final Query MATCH_NOTHING = new Query(null);

    /**
     * The query matching every document: the empty term.
     */
    public static final Query MATCH_ALL = new Query(
        new TermNode("", new TermCount(1), TermPos.FIRST)
    );

    /**
     * Root of the tree, null for a match-nothing query.
     */
    private final Node root;

    /**
     * Creates the query over a tree.
     *
     * @param root Root of the tree, null for a match-nothing query
     */
    Query(final Node root) {
        this.root = root;
    }

    /**
     * Query for a term with frequency one and no position.
     *
     * @param term The term; empty matches every document
     * @return Term query
     */
    public static Query term(final String term) {
        return Query.term(term, new TermCount(1), TermPos.FIRST);
    }

    /**
     * Query for a term with no position.
     *
     * @param term The term; empty matches every document
     * @param wqf Within-query frequency
     * @return Term query
     */
    public static Query term(final String term, final TermCount wqf) {
        return Query.term(term, wqf, TermPos.FIRST);
    }

    /**
     * Query for a term.
     *
     * @param term The term; empty matches every document
     * @param wqf Within-query frequency
     * @param pos Query position, zero for none
     * @return Term query
     */
    public static Query term(
        final String term, final TermCount wqf, final TermPos pos
    ) {
        return new Query(new TermNode(term, wqf, pos));
    }

    /**
     * Scales the weight of a query.
     *
     * @param factor Non-negative factor
     * @param subquery Query scaled; match-nothing stays match-nothing
     * @return Scaled query
     */
    public static Query scale(final double factor, final Query subquery) {
        final Query result;
        if (subquery.empty()) {
            result = Query.MATCH_NOTHING;
        } else {
            result = new Query(new ScaleNode(factor, subquery));
        }
        return result;
    }

    /**
     * Scales the weight of a query under an explicit operator.
     *
     * @param type Must be {@link QueryOp#SCALE_WEIGHT}
     * @param subquery Query scaled; match-nothing stays match-nothing and a
     *  value condition, which has no weight, stays as it is
     * @param factor Non-negative factor
     * @return Scaled query
     */
    public static Query scale(
        final QueryOp type, final Query subquery, final double factor
    ) {
        if (type != QueryOp.SCALE_WEIGHT) {
            throw new InvalidArgumentError("op must be SCALE_WEIGHT");
        }
        final Query result;
        if (subquery.empty()) {
            result = Query.MATCH_NOTHING;
        } else if (Query.weightless(subquery.type())) {
            result = subquery;
        } else {
            result = new Query(new ScaleNode(factor, subquery));
        }
        return result;
    }

    /**
     * Query for a value slot bounded on one side.
     *
     * @param type Either {@link QueryOp#VALUE_GE} or {@link QueryOp#VALUE_LE}
     * @param slot Value slot compared
     * @param limit Bound; an empty lower bound matches every document
     * @return Value query
     */
    public static Query value(
        final QueryOp type, final ValueNo slot, final String limit
    ) {
        final Query result;
        if (type == QueryOp.VALUE_GE) {
            if (limit.isEmpty()) {
                result = Query.MATCH_ALL;
            } else {
                result = new Query(new BoundNode(type, slot, limit));
            }
        } else if (type == QueryOp.VALUE_LE) {
            result = new Query(new BoundNode(type, slot, limit));
        } else {
            throw new InvalidArgumentError("op must be VALUE_LE or VALUE_GE");
        }
        return result;
    }

    /**
     * Query for a value slot within a range.
     *
     * @param slot Value slot compared
     * @param lower Lower bound; empty leaves the range open below
     * @param upper Upper bound; one below the lower bound matches nothing
     * @return Value query
     */
    public static Query valueRange(
        final ValueNo slot, final String lower, final String upper
    ) {
        final Query result;
        if (lower.isEmpty()) {
            result = Query.value(QueryOp.VALUE_LE, slot, upper);
        } else if (TermOrder.INSTANCE.compare(lower, upper) <= 0) {
            result = new Query(new RangeNode(slot, lower, upper));
        } else {
            result = Query.MATCH_NOTHING;
        }
        return result;
    }

    /**
     * Query expanding a literal prefix without limit, combined as synonyms.
     *
     * @param pattern The prefix
     * @return Wildcard query
     */
    public static Query wildcard(final String pattern) {
        return Query.wildcard(Wildcard.of(pattern));
    }

    /**
     * Query expanding a wildcard pattern.
     *
     * <p>An empty literal prefix and a glob pattern made of asterisks and
     * at most one question mark match every document; an empty glob
     * pattern matches nothing.</p>
     *
     * @param spec The pattern and how it expands
     * @return Wildcard query
     */
    public static Query wildcard(final Wildcard spec) {
        final Query result;
        if (spec.pattern().isEmpty()) {
            if (spec.glob().code() == 0) {
                result = Query.MATCH_ALL;
            } else {
                result = Query.MATCH_NOTHING;
            }
        } else if (Globs.everything(spec)) {
            result = Query.MATCH_ALL;
        } else {
            result = new Query(new WildcardNode(spec));
        }
        return result;
    }

    /**
     * Query expanding to the terms within two edits of a target, combined
     * as synonyms.
     *
     * @param target The target
     * @return Edit distance query
     */
    public static Query editDistance(final String target) {
        return Query.editDistance(EditDistance.of(target));
    }

    /**
     * Query expanding to the terms within an edit distance of a target.
     *
     * @param spec The target and how it expands
     * @return Edit distance query
     */
    public static Query editDistance(final EditDistance spec) {
        return new Query(new EditNode(spec));
    }

    /**
     * The invalid query, a placeholder.
     *
     * @return Invalid query
     */
    public static Query invalid() {
        return new Query(InvalidNode.INSTANCE);
    }

    /**
     * Combines two queries.
     *
     * @param type Operator taking subqueries
     * @param left First subquery
     * @param right Second subquery
     * @return Combined query
     */
    public static Query of(
        final QueryOp type, final Query left, final Query right
    ) {
        return Branch.pair(type, left, right);
    }

    /**
     * Combines queries.
     *
     * @param type Operator taking subqueries
     * @param subqueries Subqueries; none gives a match-nothing query
     * @return Combined query
     */
    public static Query of(
        final QueryOp type, final Iterable<Query> subqueries
    ) {
        return Query.of(type, subqueries.iterator(), 0);
    }

    /**
     * Combines queries with a window or set size.
     *
     * @param type Operator taking subqueries
     * @param subqueries Subqueries; none gives a match-nothing query
     * @param parameter Window of a positional operator or size of an elite
     *  set as an unsigned count, zero for the default
     * @return Combined query
     */
    public static Query of(
        final QueryOp type, final Iterable<Query> subqueries,
        final int parameter
    ) {
        return Query.of(type, subqueries.iterator(), parameter);
    }

    /**
     * Combines the queries of an iterator with a window or set size.
     *
     * @param type Operator taking subqueries
     * @param subqueries Subqueries; none gives a match-nothing query
     * @param parameter Window of a positional operator or size of an elite
     *  set as an unsigned count, zero for the default
     * @return Combined query
     */
    public static Query of(
        final QueryOp type, final Iterator<Query> subqueries,
        final int parameter
    ) {
        return Branch.list(type, subqueries, parameter);
    }

    /**
     * Combines two terms.
     *
     * @param type Operator taking subqueries
     * @param left First term
     * @param right Second term
     * @return Combined query
     */
    public static Query terms(
        final QueryOp type, final String left, final String right
    ) {
        return Query.terms(type, List.of(left, right), 0);
    }

    /**
     * Combines terms.
     *
     * @param type Operator taking subqueries
     * @param terms Terms; none gives a match-nothing query
     * @return Combined query
     */
    public static Query terms(final QueryOp type, final Iterable<String> terms) {
        return Query.terms(type, terms, 0);
    }

    /**
     * Combines terms with a window or set size.
     *
     * @param type Operator taking subqueries
     * @param terms Terms; none gives a match-nothing query
     * @param parameter Window of a positional operator or size of an elite
     *  set as an unsigned count, zero for the default
     * @return Combined query
     */
    public static Query terms(
        final QueryOp type, final Iterable<String> terms, final int parameter
    ) {
        return Branch.terms(type, terms.iterator(), parameter);
    }

    /**
     * Intersection with another query.
     *
     * @param other Second subquery
     * @return This AND the other
     */
    public Query and(final Query other) {
        return Query.of(QueryOp.AND, this, other);
    }

    /**
     * Union with another query.
     *
     * @param other Second subquery
     * @return This OR the other
     */
    public Query or(final Query other) {
        return Query.of(QueryOp.OR, this, other);
    }

    /**
     * Exclusive union with another query.
     *
     * @param other Second subquery
     * @return This XOR the other
     */
    public Query xor(final Query other) {
        return Query.of(QueryOp.XOR, this, other);
    }

    /**
     * Difference with another query.
     *
     * @param other Query whose matches are excluded
     * @return This AND_NOT the other
     */
    public Query andNot(final Query other) {
        return Query.of(QueryOp.AND_NOT, this, other);
    }

    /**
     * Complement: every document this query does not match.
     *
     * @return Every document AND_NOT this
     */
    public Query not() {
        return Query.MATCH_ALL.andNot(this);
    }

    /**
     * Operator or leaf type at the top of the tree.
     *
     * @return Type, {@link QueryOp#LEAF_MATCH_NOTHING} for a match-nothing
     *  query
     */
    public QueryOp type() {
        final QueryOp type;
        if (this.root == null) {
            type = QueryOp.LEAF_MATCH_NOTHING;
        } else {
            type = this.root.type();
        }
        return type;
    }

    /**
     * Number of subqueries at the top of the tree.
     *
     * @return Count, zero for a leaf or a match-nothing query
     */
    public int subqueryCount() {
        final int count;
        if (this.root == null) {
            count = 0;
        } else {
            count = this.root.subqueryCount();
        }
        return count;
    }

    /**
     * One subquery at the top of the tree.
     *
     * @param index Index below {@link #subqueryCount()}
     * @return Subquery at the index; a leaf raises an invalid-argument error
     */
    public Query subquery(final int index) {
        return this.node().subquery(index);
    }

    /**
     * Within-query frequency of a term query.
     *
     * @return Frequency; any other query raises an invalid-argument error
     */
    public TermCount wqf() {
        return this.node().wqf();
    }

    /**
     * Query position of a term query.
     *
     * @return Position; any other query raises an invalid-argument error
     */
    public TermPos pos() {
        return this.node().pos();
    }

    /**
     * Length of the query: the sum of the frequencies of its terms.
     *
     * @return Length, zero for a match-nothing query
     */
    public TermCount length() {
        final TermCount length;
        if (this.root == null) {
            length = TermCount.ZERO;
        } else {
            length = this.root.length();
        }
        return length;
    }

    /**
     * Tests whether this is a match-nothing query.
     *
     * @return True when nothing matches
     */
    public boolean empty() {
        return this.root == null;
    }

    /**
     * Terms of the query in ascending position order, a term repeated at
     * each distinct position it holds.
     *
     * @return Terms, the empty term left out
     */
    public List<String> terms() {
        return this.listing(TermAt.POSITIONAL, TermAt.POSITIONAL);
    }

    /**
     * Terms of the query sorted and without repetition.
     *
     * @return Unique terms, the empty term left out
     */
    public List<String> uniqueTerms() {
        return this.listing(TermAt.BY_TERM, TermAt.BY_TERM);
    }

    /**
     * Description of the query as the reference prints it.
     *
     * @return The tree inside {@code Query(...)}, {@code Query()} for a
     *  match-nothing query
     */
    public String description() {
        final String inner;
        if (this.root == null) {
            inner = "";
        } else {
            inner = this.root.describe();
        }
        return String.format("Query(%s)", inner);
    }

    /**
     * Serialises the query in the reference wire format.
     *
     * @return Bytes of the query, none for a match-nothing query
     */
    public byte[] serialise() {
        return Encoder.encode(this);
    }

    /**
     * Unserialises a query written in the reference wire format.
     *
     * @param data Bytes of exactly one query; none give a match-nothing query
     * @return The query; truncated data, an unknown type byte, trailing bytes
     *  or a value the Java types cannot hold raise a serialisation error
     */
    public static Query deserialise(final byte[] data) {
        return Decoder.decode(data);
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof Query query
            && Objects.equals(this.root, query.root);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.root);
    }

    @Override
    public String toString() {
        return this.description();
    }

    /**
     * Root of the tree.
     *
     * @return Root node; a match-nothing query raises an invalid-argument
     *  error
     */
    public Node node() {
        if (this.root == null) {
            throw new InvalidArgumentError(
                "Not meaningful for a match-nothing query"
            );
        }
        return this.root;
    }

    private List<String> listing(
        final Comparator<TermAt> order, final Comparator<TermAt> same
    ) {
        final List<String> result;
        if (this.root == null) {
            result = List.of();
        } else {
            result = Terms.distinct(this.root.terms().sorted(order), same);
        }
        return result;
    }

    private static boolean weightless(final QueryOp type) {
        return type == QueryOp.VALUE_RANGE || type == QueryOp.VALUE_GE
            || type == QueryOp.VALUE_LE;
    }
}
