/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Matcher core of the search engine.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.api.query.EditDistance;
import io.github.andreoss.search4j.core.api.query.EditNode;
import io.github.andreoss.search4j.core.api.query.Expansion;
import io.github.andreoss.search4j.core.api.query.Node;
import io.github.andreoss.search4j.core.api.query.Wildcard;
import io.github.andreoss.search4j.core.api.query.WildcardNode;
import io.github.andreoss.search4j.core.types.QueryOp;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Predicate;

/**
 * Terms a wildcard or edit distance subquery expands to: the fixed prefix
 * they share, the test each one passes, the limit on their number and the
 * operator combining them.
 *
 * @since 0.10.0
 */
final class ExpansionSpec {

    /**
     * Bytes every term starts with.
     */
    private final byte[] prefix;

    /**
     * Test of the bytes of a term.
     */
    private final Predicate<byte[]> check;

    /**
     * Limit on the number of terms and how it applies.
     */
    private final Expansion expansion;

    /**
     * Message when the terms exceed the limit.
     */
    private final String message;

    /**
     * Operator combining the terms.
     */
    private final QueryOp operator;

    private ExpansionSpec(
        final byte[] prefix, final Predicate<byte[]> check, final Expansion expansion,
        final String message, final QueryOp operator
    ) {
        this.prefix = prefix;
        this.check = check;
        this.expansion = expansion;
        this.message = message;
        this.operator = operator;
    }

    static ExpansionSpec of(final Node node) {
        final ExpansionSpec result;
        if (node instanceof WildcardNode wildcard) {
            result = ExpansionSpec.wildcard(wildcard.spec());
        } else {
            result = ExpansionSpec.edit(((EditNode) node).spec());
        }
        return result;
    }

    byte[] fixed() {
        return this.prefix.clone();
    }

    boolean test(final byte[] term) {
        return this.check.test(term);
    }

    Expansion limits() {
        return this.expansion;
    }

    String excess() {
        return this.message;
    }

    QueryOp combiner() {
        return this.operator;
    }

    private static ExpansionSpec wildcard(final Wildcard spec) {
        final Glob glob = Glob.of(spec);
        String pattern = spec.pattern();
        if (spec.glob().code() == 0) {
            pattern = pattern + '*';
        }
        return new ExpansionSpec(
            glob.prefix(), glob::test, spec.expansion(),
            String.format(
                "Wildcard %s expands to more than %d terms", pattern,
                spec.expansion().max().value()
            ),
            spec.combiner()
        );
    }

    private static ExpansionSpec edit(final EditDistance spec) {
        final EditCalculator calc = EditCalculator.of(spec.target());
        final byte[] bytes = spec.target().getBytes(StandardCharsets.UTF_8);
        return new ExpansionSpec(
            Arrays.copyOf(bytes, Math.min(spec.prefix(), bytes.length)),
            term -> calc.distance(term, spec.edits()) <= spec.edits(),
            spec.expansion(),
            String.format(
                "Edit distance %s~%d expands to more than %d terms", spec.target(),
                spec.edits(), spec.expansion().max().value()
            ),
            spec.combiner()
        );
    }
}
