/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Shared fixtures for the boolean posting list tests.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.backend.list.Probe;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.ArrayList;
import java.util.List;

/**
 * Documents, lists and helpers shared by the boolean posting list tests.
 *
 * @since 0.1.0
 */
final class BooleanPostListFixture {

    /**
     * Statistics of a document scored in the tests.
     */
    private static final DocStats DOC = new DocStats(
        new TermCount(10),
        new TermCount(4),
        new TermCount(3)
    );

    /**
     * Database size used by the estimate tests.
     */
    private static final TermCount SIZE = new TermCount(10);

    /**
     * Utility class, no instances.
     */
    private BooleanPostListFixture() {
    }

    static DocStats doc() {
        return BooleanPostListFixture.DOC;
    }

    static TermCount size() {
        return BooleanPostListFixture.SIZE;
    }

    static List<DocId> drain(final PostList source) {
        PostList list = source;
        final List<DocId> result = new ArrayList<>(0);
        while (!list.atEnd()) {
            list = list.next();
            if (!list.atEnd()) {
                result.add(list.docId());
            }
        }
        return result;
    }

    static List<TermPos> readPositions(final PositionList source) {
        final PositionList list = source;
        final List<TermPos> result = new ArrayList<>(0);
        while (list.next()) {
            result.add(list.position());
        }
        return result;
    }

    static List<DocId> skip(final PostList source) {
        PostList list = source;
        final List<DocId> result = new ArrayList<>(0);
        DocId target = DocId.of(1);
        while (!list.atEnd()) {
            list = list.skipTo(target);
            if (list.atEnd()) {
                break;
            }
            result.add(list.docId());
            target = list.docId().next();
        }
        return result;
    }

    static List<DocId> check(final PostList source) {
        PostList list = source;
        final List<DocId> result = new ArrayList<>(0);
        DocId target = DocId.of(1);
        final DocId limit = DocId.of(10);
        while (!list.atEnd() && target.compareTo(limit) <= 0) {
            final Probe probe = list.check(target, 0.0);
            list = probe.list();
            if (probe.valid()) {
                if (list.atEnd()) {
                    break;
                }
                result.add(list.docId());
                target = list.docId().next();
            } else {
                target = target.next();
            }
        }
        return result;
    }

    static List<DocId> ids(final int... values) {
        final List<DocId> result = new ArrayList<>(values.length);
        for (final int value : values) {
            result.add(DocId.of(value));
        }
        return result;
    }

    static BooleanPostListFixtureFake fake(final String name, final int... docs) {
        return new BooleanPostListFixtureFake(
            name,
            BooleanPostListFixture.ids(docs),
            new TermCount(docs.length),
            new DocRange(DocId.of(1), DocId.of(9)),
            List.of()
        );
    }
}
