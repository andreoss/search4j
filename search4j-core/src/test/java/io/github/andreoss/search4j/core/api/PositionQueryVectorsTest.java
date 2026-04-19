/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.error.SearchError;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks term positions at and above two to the thirty-one in serialised
 * queries against vectors captured from the reference library: bytes,
 * descriptions, lengths, decoding and decoding errors.
 *
 * @since 0.12.0
 */
final class PositionQueryVectorsTest {

    /**
     * Separator of the terms of a spec.
     */
    private static final Pattern SPACE = Pattern.compile(" ");

    @Test
    void serialisesAndDecodesPositionsAsTheReference() throws IOException {
        final Map<String, String[]> specs = new TreeMap<>();
        for (final String[] row : WeightVectors.rows("position-query-specs.tsv")) {
            specs.put(row[0], row);
        }
        final List<String> expected = new ArrayList<>(32);
        final List<String> actual = new ArrayList<>(32);
        for (final String[] row : WeightVectors.rows("position-query-vectors.tsv")) {
            expected.add(String.join("\t", row));
            actual.add(PositionQueryVectorsTest.replay(row[0], specs.get(row[1])));
        }
        Assertions.assertEquals(
            List.of(expected, 20), List.of(actual, actual.size()),
            "Bytes, descriptions, lengths and decoding of every captured query"
        );
    }

    private static String replay(final String kind, final String... spec) {
        String result;
        try {
            if ("U".equals(kind) || "E".equals(kind) && "RAW".equals(spec[1])) {
                final Query query = Query.deserialise(HexFormat.of().parseHex(spec[2]));
                result = String.join(
                    "\t", "U", spec[0], spec[2], query.toString(),
                    query.length().toString(),
                    HexFormat.of().formatHex(query.serialise())
                );
            } else {
                final Query query = PositionQueryVectorsTest.query(spec);
                final byte[] bytes = query.serialise();
                final Query back = Query.deserialise(bytes);
                result = String.join(
                    "\t", "Q", spec[0], HexFormat.of().formatHex(bytes),
                    query.toString(), query.length().toString(), back.toString(),
                    HexFormat.of().formatHex(back.serialise())
                );
            }
        } catch (final SearchError ex) {
            result = String.join("\t", "E", spec[0], ex.getType(), ex.getMessage());
        }
        return result;
    }

    private static Query query(final String... spec) {
        final Query result;
        if ("EMPTY".equals(spec[1])) {
            result = Query.term("", TermCount.parse(spec[2]), TermPos.parse(spec[3]));
        } else {
            final List<Query> leaves = new ArrayList<>(4);
            for (final String term : PositionQueryVectorsTest.SPACE.split(spec[3], -1)) {
                leaves.add(PositionQueryVectorsTest.leaf(term));
            }
            final String[] parts = spec[2].split(":", -1);
            if ("TERM".equals(parts[0])) {
                result = leaves.get(0);
            } else if (parts.length > 1) {
                result = Query.of(
                    QueryOp.valueOf(parts[0]), leaves, Integer.parseUnsignedInt(parts[1])
                );
            } else {
                result = Query.of(QueryOp.valueOf(parts[0]), leaves);
            }
        }
        return result;
    }

    private static Query leaf(final String text) {
        final int sign = text.indexOf('@');
        final int colon = text.indexOf(':');
        final int end;
        if (colon < 0) {
            end = sign;
        } else {
            end = colon;
        }
        TermCount wqf = TermCount.ONE;
        if (colon >= 0) {
            wqf = TermCount.parse(text.substring(colon + 1, sign));
        }
        TermPos pos = TermPos.FIRST;
        String name = text;
        if (sign >= 0) {
            pos = TermPos.parse(text.substring(sign + 1));
            name = text.substring(0, end);
        }
        return Query.term(name, wqf, pos);
    }
}
