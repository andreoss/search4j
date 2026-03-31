/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.types.DocId;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link PostListKeys} and {@link ChunkKey}.
 *
 * @since 0.1.0
 */
final class PostListKeysTest {

    /**
     * Term bytes of the examples.
     */
    private static final byte[] TERM = PostListKeys.bytes("ab");

    @Test
    void encodesKeysAsTheReference() {
        Assertions.assertEquals(
            List.of(
                "00e0", "00e00005", "00e0feffffffffffffffff",
                "6162", "6162000005", "616200808000", "6100ff62000005"
            ),
            List.of(
                PostListKeysTest.hex(PostListKeys.first(new byte[0])),
                PostListKeysTest.hex(
                    PostListKeys.chunk(new byte[0], DocId.of(5))
                ),
                PostListKeysTest.hex(PostListKeys.last(new byte[0])),
                PostListKeysTest.hex(PostListKeys.first(PostListKeysTest.TERM)),
                PostListKeysTest.hex(
                    PostListKeys.chunk(PostListKeysTest.TERM, DocId.of(5))
                ),
                PostListKeysTest.hex(
                    PostListKeys.chunk(PostListKeysTest.TERM, DocId.of(0x8000))
                ),
                PostListKeysTest.hex(
                    PostListKeys.chunk(PostListKeys.bytes("a\0b"), DocId.of(5))
                )
            ),
            "Lengths prefix, term with terminator, sortable document"
        );
    }

    @Test
    void ordersFirstChunkBeforeLaterChunksAndNextTerm() {
        final byte[] first = PostListKeys.first(PostListKeysTest.TERM);
        final byte[] early =
            PostListKeys.chunk(PostListKeysTest.TERM, DocId.of(7));
        final byte[] late =
            PostListKeys.chunk(PostListKeysTest.TERM, DocId.of(70_000));
        final byte[] next = PostListKeys.first(PostListKeys.bytes("ab\t"));
        Assertions.assertEquals(
            List.of(true, true, true, true, true),
            List.of(
                Arrays.compareUnsigned(first, early) < 0,
                Arrays.compareUnsigned(early, late) < 0,
                Arrays.compareUnsigned(late, next) < 0,
                Arrays.compareUnsigned(
                    late, PostListKeys.first(PostListKeys.bytes("abc"))
                ) < 0,
                Arrays.compareUnsigned(
                    PostListKeys.last(PostListKeysTest.TERM), next
                ) < 0
            ),
            "Every chunk of a term before the keys of the next term"
        );
    }

    @Test
    void findsLengthsPrefix() {
        Assertions.assertEquals(
            List.of(2, 2, 0, 0, 0),
            List.of(
                PostListKeys.lengthsPrefix(PostListKeys.first(new byte[0])),
                PostListKeys.lengthsPrefix(
                    PostListKeys.chunk(new byte[0], DocId.of(9))
                ),
                PostListKeys.lengthsPrefix(new byte[] {0}),
                PostListKeys.lengthsPrefix(new byte[] {0, (byte) 0xC0, 1}),
                PostListKeys.lengthsPrefix(PostListKeysTest.TERM)
            ),
            "Two-byte prefix of the empty term only"
        );
    }

    @Test
    void takesKeysApart() {
        final ChunkKey first = ChunkKey.of(
            ItemKey.of(PostListKeys.first(PostListKeysTest.TERM))
        );
        final ChunkKey later = ChunkKey.of(
            ItemKey.of(PostListKeys.chunk(PostListKeysTest.TERM, DocId.of(321)))
        );
        final ChunkKey lengths = ChunkKey.of(
            ItemKey.of(PostListKeys.chunk(new byte[0], DocId.of(4)))
        );
        final ChunkKey zero = ChunkKey.of(
            ItemKey.of(PostListKeys.chunk(PostListKeys.bytes("a\0b"), DocId.of(2)))
        );
        Assertions.assertEquals(
            List.of(
                true, true, false, true, false, 321, false, true, 4,
                false, true, false, true, 2
            ),
            List.of(
                first.matches(PostListKeysTest.TERM), first.first(),
                first.matches(PostListKeys.bytes("a")),
                later.matches(PostListKeysTest.TERM), later.first(),
                later.start().value(),
                lengths.matches(PostListKeysTest.TERM),
                lengths.matches(new byte[0]), lengths.start().value(),
                ChunkKey.of(ItemKey.of(PostListKeys.first(new byte[0])))
                    .matches(PostListKeysTest.TERM),
                ChunkKey.of(ItemKey.of(PostListKeys.first(new byte[0])))
                    .first(),
                zero.matches(PostListKeysTest.TERM),
                zero.matches(PostListKeys.bytes("a\0b")), zero.start().value()
            ),
            "Term and document of every key shape"
        );
    }

    @Test
    void emptyKeyMatchesNoTerm() {
        final ChunkKey key = ChunkKey.of(ItemKey.EMPTY);
        Assertions.assertEquals(
            List.of(false, false, true),
            List.of(
                key.matches(new byte[0]), key.matches(PostListKeysTest.TERM),
                key.first()
            ),
            "Root item is not a chunk of the empty term"
        );
    }

    @Test
    void keyOfOtherTermPrefixParsesAsEmptyTerm() {
        final ChunkKey key = ChunkKey.of(
            ItemKey.of(new byte[] {0, (byte) 0xC0, 0x00, 0x00, 0x07})
        );
        Assertions.assertEquals(
            List.of(true, 7),
            List.of(key.matches(new byte[0]), key.start().value()),
            "Zero byte ends the term as the reference reads it"
        );
    }

    @Test
    void rejectsFirstChunkDocument() {
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            ChunkKey.of(
                ItemKey.of(PostListKeys.first(PostListKeysTest.TERM))
            )::start,
            "No document in the key"
        );
    }

    @Test
    void rejectsBadDocumentNumbers() {
        final List<String> bad = List.of(
            "00e0ff", "00e080", "00e00000", "00e0e100000000", "00e0808000"
        );
        final List<Object> seen = Arrays.asList(new Object[bad.size()]);
        for (int idx = 0; idx < bad.size(); ++idx) {
            try {
                seen.set(
                    idx,
                    ChunkKey.of(ItemKey.of(HexFormat.of().parseHex(bad.get(idx))))
                        .start().value()
                );
            } catch (final DatabaseCorruptError ex) {
                seen.set(idx, ex.getMessage());
            }
        }
        Assertions.assertEquals(
            List.of(
                "Bad document number in key 00e0ff",
                "Bad document number in key 00e080",
                "Document number out of range in key 00e00000",
                "Document number out of range in key 00e0e100000000",
                0x8000
            ),
            seen,
            "Invalid lead, truncated, zero, above the range, valid"
        );
    }

    private static String hex(final byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
