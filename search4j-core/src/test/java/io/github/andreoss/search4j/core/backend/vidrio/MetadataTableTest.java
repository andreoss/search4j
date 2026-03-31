/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.list.TermList;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link MetadataTable}, {@link MetadataWriter},
 * {@link MetadataKeys} and {@link VidrioMetadataKeyList}: keys as the
 * reference writes them, round trips, and the key listing.
 *
 * @since 0.1.0
 */
final class MetadataTableTest {

    /**
     * A metadata key.
     */
    private static final String FOO = "foo";

    /**
     * A zero character.
     */
    private static final String NUL = Character.toString(0);

    @Test
    void roundTripsCommittedItems() {
        final Values values = Values.of();
        final MetadataWriter writer = values.metadata();
        writer.set("empty", "");
        writer.set(MetadataTableTest.FOO, "bar");
        writer.set(MetadataTableTest.NUL, MetadataTableTest.NUL);
        writer.set(MetadataTableTest.nul("", "fo"), MetadataTableTest.nul("", "xx"));
        writer.set(MetadataTableTest.nul("f", "o"), MetadataTableTest.nul("x", "x"));
        writer.set(MetadataTableTest.nul("fo", ""), MetadataTableTest.nul("xx", ""));
        writer.set("big", "j".repeat(20_000));
        values.commit();
        final MetadataTable table = MetadataTable.of(values.table());
        final List<Object> seen = new ArrayList<>(9);
        seen.add(table.get("empty"));
        seen.add(table.get("unset"));
        seen.add(table.get(MetadataTableTest.FOO));
        seen.add(table.get(MetadataTableTest.NUL));
        seen.add(table.get(MetadataTableTest.nul("", "fo")));
        seen.add(table.get(MetadataTableTest.nul("f", "o")));
        seen.add(table.get(MetadataTableTest.nul("fo", "")));
        seen.add(table.get("big").length());
        seen.add(
            Values.dump(values.postings()).stream()
                .filter(entry -> entry.startsWith("00c0666f6f=")).toList()
        );
        Assertions.assertEquals(
            List.of(
                "", "", "bar", MetadataTableTest.NUL,
                MetadataTableTest.nul("", "xx"), MetadataTableTest.nul("x", "x"),
                MetadataTableTest.nul("xx", ""),
                20_000, List.of("00c0666f6f=626172")
            ),
            seen,
            "Zero bytes kept, empty never stored, a large value split"
        );
    }

    @Test
    void readsChangesBeforeCommit() {
        final Values values = Values.of();
        final MetadataWriter writer = values.metadata();
        final MetadataTable table = MetadataTable.of(values.postings());
        final List<String> seen = new ArrayList<>(5);
        seen.add(table.get(MetadataTableTest.FOO));
        writer.set(MetadataTableTest.FOO, "bar");
        seen.add(table.get(MetadataTableTest.FOO));
        writer.set(MetadataTableTest.FOO, "baz");
        seen.add(table.get(MetadataTableTest.FOO));
        writer.set(MetadataTableTest.FOO, "");
        seen.add(table.get(MetadataTableTest.FOO));
        writer.set("2", "j".repeat(20_000));
        seen.add(table.get("2").substring(19_990));
        Assertions.assertEquals(
            List.of("", "bar", "baz", "", "jjjjjjjjjj"), seen,
            "Set, replaced, removed and read back before a commit"
        );
    }

    @Test
    void refusesEmptyAndLongKeys() {
        final Values values = Values.of();
        final MetadataTable table = MetadataTable.of(values.postings());
        final List<String> seen = new ArrayList<>(3);
        seen.add(
            Assertions.assertThrows(
                InvalidArgumentError.class, () -> table.get(""), "Empty get"
            ).getMessage()
        );
        seen.add(
            Assertions.assertThrows(
                InvalidArgumentError.class,
                () -> values.metadata().set("", MetadataTableTest.FOO), "Empty set"
            ).getMessage()
        );
        seen.add(
            Assertions.assertThrows(
                InvalidArgumentError.class,
                () -> values.metadata().set("k".repeat(254), MetadataTableTest.FOO),
                "Key over the limit"
            ).getMessage()
        );
        Assertions.assertEquals(
            List.of(
                "Empty metadata key", "Empty metadata key",
                "Key too long: length was 256 bytes, maximum length of a key is 255 bytes"
            ),
            seen,
            "The empty key and a key over the item limit are refused"
        );
    }

    @Test
    void listsKeysAsReference() {
        final List<Object> seen = new ArrayList<>(12);
        final Values values = Values.of();
        seen.add(MetadataTableTest.keys(MetadataTable.of(values.postings()), ""));
        values.metadata().set(MetadataTableTest.FOO, "val");
        values.commit();
        seen.add(MetadataTableTest.keys(MetadataTable.of(values.table()), ""));
        values.postings().add(PostListKeys.first(PostListKeys.bytes("foo")), new byte[]{1});
        values.postings().add(PostListKeys.first(new byte[0]), new byte[]{0, 0, 0, '1', 0});
        values.writer().add(DocId.of(1), Values.map(0, "v"));
        values.commit();
        seen.add(MetadataTableTest.keys(MetadataTable.of(values.table()), ""));
        values.metadata().set(MetadataTableTest.FOO, "");
        values.commit();
        seen.add(MetadataTableTest.keys(MetadataTable.of(values.table()), ""));
        for (final String key : List.of("a", MetadataTableTest.FOO, "foo1", "foo2", "z")) {
            values.metadata().set(key, "val");
        }
        values.commit();
        final MetadataTable table = MetadataTable.of(values.table());
        seen.add(MetadataTableTest.keys(table, ""));
        seen.add(MetadataTableTest.keys(table, MetadataTableTest.FOO));
        seen.add(MetadataTableTest.keys(table, "foo1"));
        seen.add(MetadataTableTest.keys(table, "fop"));
        final VidrioMetadataKeyList list = table.keys("");
        list.next();
        seen.add(list.term());
        seen.add(list.skipTo("").term());
        seen.add(list.skipTo("foo1").term());
        seen.add(list.skipTo("foo1").term());
        seen.add(list.skipTo("a").term());
        seen.add(list.next().term());
        seen.add(list.skipTo("zoo").atEnd());
        final VidrioMetadataKeyList fresh = table.keys("");
        seen.add(fresh.skipTo("fo").term());
        seen.add(fresh.skipTo("x").term());
        seen.add(fresh.skipTo("z").atEnd());
        seen.add(table.keys(MetadataTableTest.FOO).skipTo("z").atEnd());
        seen.add(table.keys("foo2").skipTo("foo1").atEnd());
        Assertions.assertEquals(
            List.of(
                List.of(), List.of(MetadataTableTest.FOO),
                List.of(MetadataTableTest.FOO), List.of(),
                List.of("a", MetadataTableTest.FOO, "foo1", "foo2", "z"),
                List.of(MetadataTableTest.FOO, "foo1", "foo2"), List.of("foo1"),
                List.of(), "a", "a", "foo1", "foo1", "foo1", "foo2", true,
                MetadataTableTest.FOO, "z", false, true, true
            ),
            seen,
            "Keys in order within a prefix, skips never backwards"
        );
    }

    @Test
    void matchesPrefixes() {
        final List<Object> seen = new ArrayList<>(4);
        final byte[] all = MetadataKeys.prefixed("");
        seen.add(MetadataKeys.matches(ItemKey.of(new byte[]{0}), all));
        seen.add(MetadataKeys.matches(ItemKey.of(all), all));
        seen.add(MetadataKeys.matches(ItemKey.of(MetadataKeys.of("ab")), all));
        seen.add(MetadataKeys.nameOf(ItemKey.of(MetadataKeys.of("ab"))));
        Assertions.assertEquals(
            List.of(false, true, true, "ab"), seen,
            "A shorter key never matches, the prefix itself and longer keys do"
        );
    }

    @Test
    void refusesReadsOffKey() {
        final Values values = Values.of();
        values.metadata().set(MetadataTableTest.FOO, "val");
        values.commit();
        final List<Object> seen = new ArrayList<>(6);
        final VidrioMetadataKeyList list = MetadataTable.of(values.table()).keys("");
        seen.add(MetadataTableTest.refused(list::term));
        seen.add(list.approxSize());
        seen.add(MetadataTableTest.refused(list::termFrequency));
        seen.add(MetadataTableTest.refused(list::wdf));
        list.next();
        seen.add(list.term());
        list.next();
        seen.add(MetadataTableTest.refused(list::term));
        seen.add(list.next().atEnd());
        Assertions.assertEquals(
            List.of(
                "Metadata key list is not on a key", new TermCount(1),
                "Term frequency is not meaningful for a metadata key list",
                "Within-document frequency is not meaningful for a term listing",
                MetadataTableTest.FOO, "Metadata key list is not on a key", true
            ),
            seen,
            "Before the first move, after the end, no frequencies"
        );
    }

    private static List<String> keys(final MetadataTable table, final String prefix) {
        final List<String> result = new ArrayList<>(0);
        final TermList list = table.keys(prefix);
        list.next();
        while (!list.atEnd()) {
            result.add(list.term());
            list.next();
        }
        return result;
    }

    private static String nul(final String before, final String after) {
        return String.join(MetadataTableTest.NUL, before, after);
    }

    private static String refused(final Runnable call) {
        String result = "none";
        try {
            call.run();
        } catch (final InvalidOperationError ex) {
            result = ex.getMessage();
        }
        return result;
    }
}
