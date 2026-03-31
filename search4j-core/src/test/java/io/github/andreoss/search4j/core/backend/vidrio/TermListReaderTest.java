/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.types.TermCount;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link TermListReader}: round trips and every damaged tag.
 *
 * @since 0.1.0
 */
final class TermListReaderTest {

    @Test
    void readsEmptyTag() {
        final TermListReader reader = TermListReader.parse(new byte[0]);
        Assertions.assertEquals(
            List.of(TermCount.ZERO, TermCount.ZERO, false),
            List.of(reader.length(), reader.size(), reader.advance()),
            "No length, no terms"
        );
    }

    @Test
    void readsBackWhatWasWritten() {
        final Map<String, TermCount> terms = new LinkedHashMap<>();
        terms.put("ab", new TermCount(127));
        terms.put("ac", new TermCount(126));
        terms.put("ad", new TermCount(83));
        terms.put("ae", new TermCount(84));
        terms.put("af", new TermCount(1000));
        terms.put("afa", TermCount.ZERO);
        terms.put("y".repeat(255), new TermCount(1));
        terms.put("é", new TermCount(2));
        terms.put(String.format("é%s", "z".repeat(200)), new TermCount(3));
        terms.put(String.format("é%s", "z".repeat(201)), new TermCount(4));
        final TermListReader reader =
            TermListReader.parse(TermListTag.encode(terms));
        final List<String> seen = new ArrayList<>(terms.size());
        while (reader.advance()) {
            seen.add(
                String.format(
                    "%s:%s",
                    new String(reader.term(), StandardCharsets.UTF_8),
                    reader.wdf()
                )
            );
        }
        final List<String> expected = new ArrayList<>(terms.size());
        for (final Map.Entry<String, TermCount> term : terms.entrySet()) {
            expected.add(String.format("%s:%s", term.getKey(), term.getValue()));
        }
        Assertions.assertEquals(
            List.of(expected, new TermCount(1430), new TermCount(10)),
            List.of(seen, reader.length(), reader.size()),
            "Every form of the reuse byte, prefixes above 127 bytes"
        );
    }

    @Test
    void rejectsDamagedTags() {
        final Map<String, String> cases = new LinkedHashMap<>();
        cases.put("80", "Document length out of data");
        cases.put("ffffffff7f", "Document length overflowed");
        cases.put("03", "Term count out of data");
        cases.put("0380", "Term count out of data");
        cases.put("0301", "Term count 1 does not match the 0 terms held");
        cases.put(
            "0301016102020162", "Term count 1 does not match the 2 terms held"
        );
        cases.put("0302016101", "Term count 2 does not match the 1 terms held");
        cases.put("030102", "Term bytes out of data");
        cases.put("03010261", "Term bytes out of data");
        cases.put("0301016180", "Within-document frequency out of data");
        cases.put("03010161ffffffff7f", "Within-document frequency overflowed");
        cases.put(
            "03010161ffffffff0f",
            "Within-document frequency too large: 4294967295"
        );
        cases.put("030201610202", "Term length out of data");
        cases.put(
            "0302016101020162020163",
            "Term count 2 does not match the 3 terms held"
        );
        final List<String> seen = new ArrayList<>(cases.size());
        for (final String hex : cases.keySet()) {
            seen.add(
                Assertions.assertThrows(
                    DatabaseCorruptError.class,
                    () -> TermListReaderTest.drain(hex), hex
                ).getMessage()
            );
        }
        Assertions.assertEquals(
            new ArrayList<>(cases.values()), seen, "Message of every damage"
        );
    }

    private static void drain(final String hex) {
        final TermListReader reader =
            TermListReader.parse(HexFormat.of().parseHex(hex));
        while (reader.advance()) {
            reader.term();
        }
    }
}
