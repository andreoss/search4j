/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Compatibility checks of this project against the reference sources and
 * the reference-produced fixtures.
 */
package io.github.andreoss.search4j.core.compat;

import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.backend.Uuid;
import io.github.andreoss.search4j.core.backend.codec.PrefixCompressedWriter;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.backend.vidrio.BlockSize;
import io.github.andreoss.search4j.core.backend.vidrio.DatabaseStats;
import io.github.andreoss.search4j.core.backend.vidrio.DocDataKeys;
import io.github.andreoss.search4j.core.backend.vidrio.TableCursor;
import io.github.andreoss.search4j.core.backend.vidrio.TableType;
import io.github.andreoss.search4j.core.backend.vidrio.VersionFile;
import io.github.andreoss.search4j.core.backend.vidrio.VidrioFormat;
import io.github.andreoss.search4j.core.backend.vidrio.VidrioTable;
import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.matcher.AndMaybePostList;
import io.github.andreoss.search4j.core.matcher.AndNotPostList;
import io.github.andreoss.search4j.core.matcher.AndPostList;
import io.github.andreoss.search4j.core.matcher.BoolOrPostList;
import io.github.andreoss.search4j.core.matcher.OrPostList;
import io.github.andreoss.search4j.core.matcher.XorPostList;
import io.github.andreoss.search4j.core.ser.BitWriter;
import io.github.andreoss.search4j.core.ser.SortableDoubles;
import io.github.andreoss.search4j.core.ser.SortableStrings;
import io.github.andreoss.search4j.core.ser.SortableUints;
import io.github.andreoss.search4j.core.ser.Varints;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Reference-derived expectations of every held fixture, every held byte
 * vector, the query texts, the query wire form and the boolean operators.
 *
 * <p>The checks run in a fixed order and the last one prints the coverage
 * summary naming every covered and every uncovered surface.</p>
 *
 * @since 0.1.0
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
final class CompatibilityOracleTest {

    /**
     * Documents of the reference database.
     */
    private static final int DOCS = 112;

    /**
     * Data of every document.
     */
    private static final String DATA = "hello world";

    /**
     * Offset of the identifier in a version record.
     */
    private static final int UUID_START = 16;

    /**
     * Length of a document data tag in the reference database.
     */
    private static final int TAG = 11;

    /**
     * Compression threshold of the document data and term list tables.
     */
    private static final int COMPRESS = 18;

    /**
     * Tag length written when reading the item raises a corrupt error.
     */
    private static final int DAMAGED = -1;

    /**
     * Range of documents the operator members may hold.
     */
    private static final int LAST = 9;

    @Test
    @Order(1)
    void checksEveryHeldFixture() throws IOException, URISyntaxException {
        final Map<String, Integer> sizes = new LinkedHashMap<>(0);
        sizes.put("oversized/version", 95);
        sizes.put("oversized/postlist", BlockSize.MIN_BYTES);
        sizes.put("oversized/termlist", BlockSize.MIN_BYTES);
        sizes.put("oversized/docdata", BlockSize.MIN_BYTES);
        sizes.put("oversized/docdata2", BlockSize.MIN_BYTES);
        for (int num = 1; num <= 4; ++num) {
            sizes.put(String.format("single/db%d", num), BlockSize.MIN_BYTES);
        }
        final List<Integer> seen = new ArrayList<>(0);
        for (final Map.Entry<String, Integer> size : sizes.entrySet()) {
            seen.add(ReferenceFixtures.bytes(size.getKey()).length);
        }
        Assertions.assertEquals(
            sizes.keySet().stream().sorted().toList(),
            ReferenceFixtures.names(),
            "The manifest contains every held binary fixture"
        );
        Assertions.assertEquals(
            List.copyOf(sizes.values()), seen, "Every held fixture read"
        );
        for (int num = 1; num <= 4; ++num) {
            final int database = num;
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> VidrioTable.open(
                    ReferenceFixtures.path(
                        String.format("single/db%d", database)
                    ),
                    ReferenceFixtures.root()
                ),
                String.format("Reference single-file database %d", database)
            );
        }
        Surfaces.covered("vidrio.database.single");
    }

    @Test
    @Order(2)
    void checksTheVersionRecord(@TempDir final Path dir) throws IOException {
        Files.write(
            dir.resolve(VidrioFormat.versionFileName()),
            ReferenceFixtures.bytes("oversized/version")
        );
        final VersionFile version = new VersionFile(dir);
        version.read();
        Assertions.assertEquals(
            List.of(
                "a57cce6e-c41f-4638-811b-f5f9ddff108f", 1L,
                new DatabaseStats(
                    CompatibilityOracleTest.DOCS,
                    CompatibilityOracleTest.DOCS,
                    0L, 0L, 0L, 0L, 0L, 0L
                )
            ),
            List.of(
                version.uuid().toString(), version.revision().value(),
                version.stats()
            ),
            "Identifier, revision and statistics of the reference database"
        );
        final List<TableType> eager = List.of(
            TableType.POSTLIST, TableType.DOCDATA, TableType.TERMLIST
        );
        Assertions.assertEquals(
            List.of(
                List.of(1L, 112L, 112L),
                List.of(
                    0, CompatibilityOracleTest.COMPRESS,
                    CompatibilityOracleTest.COMPRESS
                ),
                List.of(0, 0, 0),
                List.of(
                    BlockSize.MIN_BYTES, BlockSize.MIN_BYTES,
                    BlockSize.MIN_BYTES
                ),
                List.of(1L, 1L, 1L),
                List.of(false, false, false),
                List.of(true, true, true)
            ),
            List.of(
                eager.stream().map(type -> version.root(type).entries().value())
                    .toList(),
                eager.stream().map(type -> version.root(type).compress())
                    .toList(),
                eager.stream().map(type -> version.root(type).root().level())
                    .toList(),
                eager.stream()
                    .map(type -> version.root(type).root().size().bytes())
                    .toList(),
                eager.stream()
                    .map(type -> version.root(type).root().revision().value())
                    .toList(),
                eager.stream().map(type -> version.root(type).root().fake())
                    .toList(),
                eager.stream().map(type -> version.root(type).sequential())
                    .toList()
            ),
            "Roots of the three tables written by the reference"
        );
        final List<TableType> lazy = List.of(
            TableType.POSITION, TableType.SPELLING, TableType.SYNONYM
        );
        Assertions.assertEquals(
            List.of(List.of(0L, 0L, 0L), List.of(true, true, true)),
            List.of(
                lazy.stream().map(type -> version.root(type).entries().value())
                    .toList(),
                lazy.stream().map(type -> version.root(type).root().fake())
                    .toList()
            ),
            "Tables the reference never wrote"
        );
        Surfaces.covered("vidrio.version.record");
    }

    @Test
    @Order(3)
    void checksTheTables() throws IOException, URISyntaxException {
        Assertions.assertEquals(
            List.of("00e0=228"),
            CompatibilityOracleTest.items(
                ReferenceFixtures.path("oversized/postlist")
            ),
            "One posting entry, the all-documents chunk"
        );
        Surfaces.covered("vidrio.table.postlist");
        final List<String> terms = CompatibilityOracleTest.items(
            ReferenceFixtures.path("oversized/termlist")
        );
        Assertions.assertEquals(
            List.of(
                CompatibilityOracleTest.DOCS, "0001=0", "0070=0",
                (long) CompatibilityOracleTest.DOCS
            ),
            List.of(
                terms.size(), terms.get(0), terms.get(terms.size() - 1),
                terms.stream().filter(item -> item.endsWith("=0")).count()
            ),
            "One empty term list per document"
        );
        Surfaces.covered("vidrio.table.termlist");
        final List<String> data = CompatibilityOracleTest.items(
            ReferenceFixtures.path("oversized/docdata")
        );
        Assertions.assertEquals(
            List.of(
                CompatibilityOracleTest.DOCS,
                String.format("0001=%d", CompatibilityOracleTest.TAG),
                String.format("006f=%d", CompatibilityOracleTest.TAG),
                String.format("0070=%d", CompatibilityOracleTest.DAMAGED),
                (long) (CompatibilityOracleTest.DOCS - 1),
                (long) CompatibilityOracleTest.DOCS
            ),
            List.of(
                data.size(), data.get(0), data.get(data.size() - 2),
                data.get(data.size() - 1),
                data.stream().filter(
                    item -> item.endsWith(
                        String.format("=%d", CompatibilityOracleTest.TAG)
                    )
                ).count(),
                data.stream().filter(item -> item.startsWith("00")).count()
            ),
            "One document data entry per document, the last one damaged"
        );
        for (final int doc : List.of(1, 57, 111)) {
            Assertions.assertEquals(
                CompatibilityOracleTest.DATA,
                CompatibilityOracleTest.tag("oversized/docdata", doc),
                String.format("Document %d", doc)
            );
        }
        Surfaces.covered("vidrio.table.docdata");
        final List<String> second = CompatibilityOracleTest.items(
            ReferenceFixtures.path("oversized/docdata2")
        );
        Assertions.assertEquals(
            List.of(
                CompatibilityOracleTest.DOCS,
                String.format("0070=%d", 1041)
            ),
            List.of(second.size(), second.get(second.size() - 1)),
            "Last item oversized, still inside the block"
        );
        Surfaces.covered("vidrio.table.docdata2");
    }

    @Test
    @Order(4)
    void rewritesTheReferenceBlocks(@TempDir final Path dir)
        throws IOException, URISyntaxException {
        ReferenceDatabaseOracle.build(
            dir, CompatibilityOracleTest.DOCS, CompatibilityOracleTest.DATA
        );
        Assertions.assertArrayEquals(
            ReferenceFixtures.bytes("oversized/termlist"),
            Files.readAllBytes(dir.resolve(TableType.TERMLIST.fileName())),
            "Term list blocks byte for byte"
        );
        Assertions.assertEquals(
            CompatibilityOracleTest.items(
                ReferenceFixtures.path("oversized/postlist")
            ),
            CompatibilityOracleTest.items(
                dir.resolve(TableType.POSTLIST.fileName())
            ),
            "Posting entries of the rewritten table"
        );
        Assertions.assertEquals(
            CompatibilityOracleTest.items(
                ReferenceFixtures.path("oversized/docdata")
            ).subList(0, CompatibilityOracleTest.DOCS - 1),
            CompatibilityOracleTest.items(
                dir.resolve(TableType.DOCDATA.fileName())
            ).subList(0, CompatibilityOracleTest.DOCS - 1),
            "Document data outside the damaged item"
        );
        final byte[] expected = ReferenceFixtures.bytes("oversized/version");
        final byte[] actual = Files.readAllBytes(
            dir.resolve(VidrioFormat.versionFileName())
        );
        final int flags = CompatibilityOracleTest.UUID_START + Uuid.BYTES + 2;
        for (final byte[] record : List.of(expected, actual)) {
            Arrays.fill(
                record, CompatibilityOracleTest.UUID_START,
                CompatibilityOracleTest.UUID_START + Uuid.BYTES, (byte) 0
            );
            record[flags] = 0;
        }
        Assertions.assertArrayEquals(
            expected, actual, "Version record apart from the identifier"
        );
        Surfaces.covered("vidrio.write.identity");
    }

    @Test
    @Order(5)
    void readsTheReferenceDatabase(@TempDir final Path dir) throws IOException {
        Assertions.assertEquals(
            new TermCount(CompatibilityOracleTest.DOCS),
            ReferenceDatabaseOracle.read(
                dir, CompatibilityOracleTest.DOCS, CompatibilityOracleTest.DATA
            ),
            "Reference database document count"
        );
        Assertions.assertEquals(
            List.of(
                new TermCount(2), List.of(new TermPos(2), new TermPos(5)),
                Optional.of("value"), "value", "key"
            ),
            ReferenceDatabaseOracle.auxiliary(dir),
            "Positions, values, metadata and metadata keys"
        );
        Surfaces.covered("vidrio.positions");
        Surfaces.covered("vidrio.values");
        Surfaces.covered("vidrio.metadata");
    }

    @Test
    @Order(6)
    void checksTheSortableIntegers() {
        final String[][] bands = {
            {"0", "0000"},
            {"7fff", "7fff"},
            {"8000", "808000"},
            {"3fffff", "bfffff"},
            {"400000", "c0400000"},
            {"1fffffff", "dfffffff"},
            {"20000000", "e020000000"},
            {"ffffffffffffffff", "feffffffffffffffff"},
        };
        Assertions.assertEquals(
            Arrays.stream(bands).map(band -> band[1]).toList(),
            Arrays.stream(bands).map(
                band -> HexFormat.of().formatHex(
                    SortableUints.pack(
                        Long.parseUnsignedLong(band[0], 16)
                    )
                )
            )
            .toList(),
            "Every band boundary and the widest value"
        );
        Surfaces.covered("ser.sortable.uints");
    }

    @Test
    @Order(7)
    void checksTheSortableStrings() {
        Assertions.assertEquals(
            List.of("616263", "61626300", "6100ff62", "00", ""),
            List.of(
                CompatibilityOracleTest.text("abc", true),
                CompatibilityOracleTest.text("abc", false),
                CompatibilityOracleTest.text("a\0b", true),
                CompatibilityOracleTest.text("", false),
                new String(
                    SortableStrings.unpack(
                        ByteBuffer.wrap(
                            new byte[] {0x00}
                        )
                    ),
                    StandardCharsets.US_ASCII
                )
            ),
            "Zero bytes escaped, terminator unless last"
        );
        Surfaces.covered("ser.sortable.strings");
    }

    @Test
    @Order(8)
    void checksTheSortableDoubles() {
        Assertions.assertEquals(
            List.of(
                "a7", "59c0", "80", "", "ffffffffffffffffff",
                3.5, -2.25, 0.0,
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY
            ),
            List.of(
                HexFormat.of().formatHex(SortableDoubles.serialise(3.5)),
                HexFormat.of().formatHex(SortableDoubles.serialise(-2.25)),
                HexFormat.of().formatHex(SortableDoubles.serialise(0.0)),
                HexFormat.of().formatHex(
                    SortableDoubles.serialise(Double.NEGATIVE_INFINITY)
                ),
                HexFormat.of().formatHex(
                    SortableDoubles.serialise(Double.POSITIVE_INFINITY)
                ),
                SortableDoubles.deserialise(
                    SortableDoubles.serialise(3.5)
                ),
                SortableDoubles.deserialise(
                    SortableDoubles.serialise(-2.25)
                ),
                SortableDoubles.deserialise(SortableDoubles.serialise(0.0)),
                SortableDoubles.deserialise(
                    SortableDoubles.serialise(Double.NEGATIVE_INFINITY)
                ),
                SortableDoubles.deserialise(
                    SortableDoubles.serialise(Double.POSITIVE_INFINITY)
                )
            ),
            "Reference sortable doubles"
        );
        Surfaces.covered("ser.sortable.doubles");
    }

    @Test
    @Order(9)
    void checksTheVariableIntegers() {
        final String[][] bands = {
            {"0", "00"},
            {"7f", "7f"},
            {"80", "8001"},
            {"12c", "ac02"},
            {"4000", "808001"},
        };
        Assertions.assertEquals(
            Arrays.stream(bands).map(band -> band[1]).toList(),
            Arrays.stream(bands).map(
                band -> HexFormat.of().formatHex(
                    Varints.pack(Long.parseLong(band[0], 16))
                )
            ).toList(),
            "Seven bits per byte, least significant group first"
        );
        Surfaces.covered("ser.varints");
    }

    @Test
    @Order(10)
    void checksThePrefixCompressedKeys() {
        final PrefixCompressedWriter writer = new PrefixCompressedWriter();
        writer.append("abc".getBytes(StandardCharsets.UTF_8));
        writer.append("abde".getBytes(StandardCharsets.UTF_8));
        Assertions.assertEquals(
            "6361626362626465",
            HexFormat.of().formatHex(writer.bytes()).replace(" ", ""),
            "Shared prefix kept, rest added"
        );
        Surfaces.covered("codec.prefix");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("descriptions")
    @Order(11)
    void describesAsTheReference(final String name, final Query query,
        final String expected) {
        Assertions.assertEquals(expected, query.description(), name);
        Surfaces.covered("query.description");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource({"shapes", "terms"})
    @Order(12)
    void writesTheReferenceBytes(final String name, final Query query,
        final String expected) {
        Assertions.assertEquals(
            expected, HexFormat.of().formatHex(query.serialise()), name
        );
        Surfaces.covered("query.wire");
    }

    @Test
    @Order(13)
    void checksTheBooleanOperators() {
        Assertions.assertEquals(
            List.of(
                CompatibilityOracleTest.ids(3, 7),
                CompatibilityOracleTest.ids(1, 2, 3, 5, 6),
                CompatibilityOracleTest.ids(1, 3),
                CompatibilityOracleTest.ids(1, 3, 5),
                CompatibilityOracleTest.ids(1, 2, 3, 4),
                CompatibilityOracleTest.ids(1, 2, 5, 6),
                CompatibilityOracleTest.ids(3),
                List.of(0L, 0L, 0L, 0L, 0L, 0L)
            ),
            List.of(
                CompatibilityOracleTest.drain(
                    new AndPostList(
                        List.of(
                            new OraclePostList("A", 1, 3, 5, 7),
                            new OraclePostList("B", 2, 3, 6, 7)
                        ),
                        () -> { }
                    )
                ),
                CompatibilityOracleTest.drain(
                    new OrPostList(
                        new OraclePostList("A", 1, 3, 5),
                        new OraclePostList("B", 2, 3, 6),
                        () -> { }
                    )
                ),
                CompatibilityOracleTest.drain(
                    new AndNotPostList(
                        new OraclePostList("A", 1, 2, 3, 4),
                        new OraclePostList("B", 2, 4),
                        new TermCount(CompatibilityOracleTest.LAST)
                    )
                ),
                CompatibilityOracleTest.drain(
                    new AndMaybePostList(
                        new OraclePostList("A", 1, 3, 5),
                        new OraclePostList("B", 3, 5, 7),
                        () -> { }
                    )
                ),
                CompatibilityOracleTest.drain(
                    new BoolOrPostList(
                        List.of(
                            new OraclePostList("A", 1, 3),
                            new OraclePostList("B", 2, 3, 4)
                        ),
                        new TermCount(CompatibilityOracleTest.LAST)
                    )
                ),
                CompatibilityOracleTest.drain(
                    new XorPostList(
                        List.of(
                            new OraclePostList("A", 1, 3, 5),
                            new OraclePostList("B", 2, 3, 6)
                        ),
                        new TermCount(CompatibilityOracleTest.LAST),
                        () -> { }
                    )
                ),
                CompatibilityOracleTest.drain(
                    new XorPostList(
                        List.of(
                            new OraclePostList("A", 1),
                            new OraclePostList("B", 1, 2),
                            new OraclePostList("C", 2, 3)
                        ),
                        new TermCount(CompatibilityOracleTest.LAST),
                        () -> { }
                    )
                ),
                List.of(0L, 0L, 0L, 0L, 0L, 0L)
            ),
            "Intersections, unions, differences and exclusive unions"
        );
        Assertions.assertEquals(
            List.of(1L, 2L, 2L),
            CompatibilityOracleTest.matching(
                new AndMaybePostList(
                    new OraclePostList("A", 1, 3, 5),
                    new OraclePostList("B", 3, 5, 7),
                    () -> { }
                )
            ),
            "Matching subqueries at every document of an optional intersection"
        );
        Surfaces.covered("matcher.boolean");
    }

    @Test
    @Order(14)
    void checksReferenceBitOrder() {
        final BitWriter writer = new BitWriter();
        writer.encode(9L, 11L);
        writer.encode(3L, 11L);
        writer.encode(0L, 1L);
        writer.encode(5L, 8L);
        Assertions.assertEquals(
            "b902", HexFormat.of().formatHex(writer.freeze()),
            "Reference least-significant-bit-first vector"
        );
        Surfaces.covered("ser.bitstream");
    }

    @Test
    @Order(15)
    void printsTheCoverageSummary() {
        final List<String> lines = Surfaces.report();
        lines.forEach(System.out::println);
        Assertions.assertEquals(
            0L, Surfaces.missed(), "Every checked surface compared"
        );
    }

    static Stream<Arguments> descriptions() {
        return Stream.of(
            Arguments.of(
                "match all", Query.MATCH_ALL, "Query(<alldocuments>)"
            ),
            Arguments.of("match nothing", Query.MATCH_NOTHING, "Query()"),
            Arguments.of("term", Query.term("foo"), "Query(foo)"),
            Arguments.of(
                "frequency", Query.term("foo", new TermCount(2)), "Query(foo#2)"
            ),
            Arguments.of(
                "position",
                Query.term("foo", new TermCount(1), new TermPos(3)),
                "Query(foo@3)"
            ),
            Arguments.of(
                "intersection", Query.term("foo").and(Query.term("bar")),
                "Query((foo AND bar))"
            ),
            Arguments.of(
                "union", Query.term("foo").or(Query.term("bar")),
                "Query((foo OR bar))"
            ),
            Arguments.of(
                "difference", Query.term("foo").andNot(Query.term("bar")),
                "Query((foo AND_NOT bar))"
            ),
            Arguments.of(
                "exclusive union", Query.term("foo").xor(Query.term("bar")),
                "Query((foo XOR bar))"
            ),
            Arguments.of(
                "optional intersection",
                Query.of(QueryOp.AND_MAYBE, Query.term("a"), Query.term("b")),
                "Query((a AND_MAYBE b))"
            ),
            Arguments.of(
                "filtered intersection",
                Query.of(QueryOp.FILTER, Query.term("a"), Query.term("b")),
                "Query((a FILTER b))"
            ),
            Arguments.of(
                "elite set",
                Query.of(QueryOp.ELITE_SET, Query.term("a"), Query.term("b")),
                "Query((a ELITE_SET 10 b))"
            ),
            Arguments.of(
                "synonyms",
                Query.of(QueryOp.SYNONYM, Query.term("a"), Query.term("b")),
                "Query((a SYNONYM b))"
            ),
            Arguments.of(
                "maximum",
                Query.of(QueryOp.MAX, Query.term("a"), Query.term("b")),
                "Query((a MAX b))"
            ),
            Arguments.of(
                "phrase",
                Query.of(
                    QueryOp.PHRASE,
                    Query.term("a").or(Query.term("b")),
                    Query.term("a").or(Query.term("b"))
                ),
                "Query(((a OR b) PHRASE 2 (a OR b)))"
            ),
            Arguments.of(
                "near",
                Query.of(
                    QueryOp.NEAR,
                    Query.term("a").or(Query.term("b")),
                    Query.term("a").or(Query.term("b"))
                ),
                "Query(((a OR b) NEAR 2 (a OR b)))"
            ),
            Arguments.of(
                "scale",
                Query.scale(1.25, Query.term("one").or(Query.term("two"))),
                "Query(1.25 * (one OR two))"
            ),
            Arguments.of(
                "value range",
                Query.valueRange(new ValueNo(7L), "a", "a"),
                "Query(VALUE_RANGE 7 a a)"
            ),
            Arguments.of(
                "lower bound",
                Query.value(QueryOp.VALUE_GE, new ValueNo(1L), "m"),
                "Query(VALUE_GE 1 m)"
            ),
            Arguments.of(
                "upper bound",
                Query.value(QueryOp.VALUE_LE, new ValueNo(1L), "m"),
                "Query(VALUE_LE 1 m)"
            ),
            Arguments.of(
                "wildcard", Query.wildcard("fo"), "Query(WILDCARD SYNONYM fo)"
            ),
            Arguments.of(
                "escaped term", Query.term("back\\slash"),
                "Query(back\\x5cslash)"
            )
        );
    }

    static Stream<Arguments> shapes() {
        return Stream.of(
            Arguments.of("match all", Query.MATCH_ALL, "0f"),
            Arguments.of(
                "intersection",
                Query.term("foo").and(Query.term("bar")),
                String.format(
                    "82%s%s",
                    CompatibilityOracleTest.term("foo"),
                    CompatibilityOracleTest.term("bar")
                )
            ),
            Arguments.of(
                "union of two",
                Query.term("foo").or(Query.term("bar")),
                String.format(
                    "8a%s%s",
                    CompatibilityOracleTest.term("foo"),
                    CompatibilityOracleTest.term("bar")
                )
            ),
            Arguments.of(
                "union beyond seven members",
                Query.terms(
                    QueryOp.OR,
                    List.of(
                        "a", "b", "c", "d", "e", "f", "g", "h"
                    )
                ),
                String.format(
                    "8800%s",
                    Stream.of("a", "b", "c", "d", "e", "f", "g", "h")
                        .map(CompatibilityOracleTest::term)
                        .collect(Collectors.joining())
                )
            )
        );
    }

    static Stream<Arguments> terms() {
        return Stream.of(
            Arguments.of(
                "free text term", Query.term("foo"),
                CompatibilityOracleTest.term("foo")
            ),
            Arguments.of(
                "term with position",
                Query.term("foo", new TermCount(1), new TermPos(2)),
                String.format("63%s02", CompatibilityOracleTest.utf("foo"))
            ),
            Arguments.of(
                "sixteen letters",
                Query.term("abcdefghijklmnop"),
                String.format(
                    "5000%s",
                    CompatibilityOracleTest.utf("abcdefghijklmnop")
                )
            )
        );
    }

    private static String term(final String text) {
        return String.format(
            "%02x%s", 0x40 | 0x10 | text.length(),
            CompatibilityOracleTest.utf(text)
        );
    }

    private static String utf(final String text) {
        return HexFormat.of().formatHex(
            text.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String text(final String value, final boolean last) {
        return HexFormat.of().formatHex(
            SortableStrings.pack(
                value.getBytes(StandardCharsets.UTF_8), last
            )
        );
    }

    private static String tag(final String name, final int doc)
        throws URISyntaxException {
        final String result;
        try (
            VidrioTable table = VidrioTable.open(
                ReferenceFixtures.path(name), ReferenceFixtures.root()
            )
        ) {
            result = new String(
                table.get(DocDataKeys.of(DocId.of(doc)).data()).orElseThrow(),
                StandardCharsets.US_ASCII
            );
        }
        return result;
    }

    private static List<String> items(final Path path) {
        final List<String> result = new ArrayList<>(0);
        try (
            VidrioTable table = VidrioTable.open(
                path, ReferenceFixtures.root()
            )
        ) {
            final TableCursor cursor = table.cursor();
            cursor.rewind();
            while (cursor.next()) {
                try {
                    result.add(
                        String.format(
                            "%s=%d",
                            HexFormat.of().formatHex(cursor.key().data()),
                            cursor.tag().length
                        )
                    );
                } catch (final DatabaseCorruptError ex) {
                    result.add(
                        String.format(
                            "%s=%d",
                            HexFormat.of().formatHex(cursor.key().data()),
                            CompatibilityOracleTest.DAMAGED
                        )
                    );
                }
            }
        }
        return result;
    }

    private static List<DocId> drain(final PostList source) {
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

    private static List<Long> matching(final PostList source) {
        final List<Long> result = new ArrayList<>(0);
        PostList list = source;
        while (!list.atEnd()) {
            list = list.next();
            if (!list.atEnd()) {
                result.add((long) list.countMatchingSubqueries().value());
            }
        }
        return result;
    }

    private static List<DocId> ids(final int... values) {
        final List<DocId> result = new ArrayList<>(values.length);
        for (final int value : values) {
            result.add(DocId.of(value));
        }
        return result;
    }
}
