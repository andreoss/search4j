/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.TermFrequencies;
import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.InvariantError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link FirstHeader}, {@link ChunkHeader}, {@link PostingChunk}
 * and {@link PostingFields}.
 *
 * @since 0.1.0
 */
final class ChunkHeadersTest {

    @Test
    void encodesFirstHeaderAsTheReference() {
        Assertions.assertEquals(
            "ac02e8078001",
            HexFormat.of().formatHex(
                new FirstHeader(
                    new TermFrequencies(new TermCount(300), new TermCount(1000)),
                    DocId.of(129)
                ).bytes()
            ),
            "Term frequency, collection frequency, first document less one"
        );
    }

    @Test
    void readsFirstHeaderBack() {
        final FirstHeader head = new FirstHeader(
            new TermFrequencies(new TermCount(3), new TermCount(7)),
            DocId.of(1)
        );
        final ByteReader reader = ByteReader.over(head.bytes());
        Assertions.assertEquals(
            List.of(head, true),
            List.of(FirstHeader.read(reader), reader.done()),
            "Round trip"
        );
    }

    @Test
    void encodesChunkHeaderAsTheReference() {
        Assertions.assertEquals(
            List.of("3100", "30ff01"),
            List.of(
                HexFormat.of().formatHex(
                    new ChunkHeader(true, DocId.of(5)).bytes(DocId.of(5))
                ),
                HexFormat.of().formatHex(
                    new ChunkHeader(false, DocId.of(260)).bytes(DocId.of(5))
                )
            ),
            "Flag digit and the distance to the last document"
        );
    }

    @Test
    void readsChunkHeaderBack() {
        final ChunkHeader head = new ChunkHeader(false, DocId.of(90));
        final ByteReader reader = ByteReader.over(head.bytes(DocId.of(10)));
        Assertions.assertEquals(
            List.of(head, true),
            List.of(ChunkHeader.read(reader, DocId.of(10)), reader.done()),
            "Round trip"
        );
    }

    @Test
    void rejectsChunkEndingBeforeItStarts() {
        final ChunkHeader head = new ChunkHeader(true, DocId.of(4));
        Assertions.assertThrows(
            InvariantError.class, () -> head.bytes(DocId.of(5)),
            "Last document before the first"
        );
    }

    @Test
    void rejectsHeadersOutOfRange() {
        final List<String> bad = List.of(
            "ffffffff0f", "00ffffffff0f", "0000ffffffff0f", "000000",
            "00000031ffffffff0f", "0000003100"
        );
        final List<Object> seen = new ArrayList<>(bad.size());
        for (final String item : bad) {
            try {
                final ByteReader reader =
                    ByteReader.over(HexFormat.of().parseHex(item));
                final FirstHeader head = FirstHeader.read(reader);
                seen.add(
                    ChunkHeader.read(reader, head.first()).end().value()
                );
            } catch (final DatabaseCorruptError ex) {
                seen.add(ex.getMessage());
            }
        }
        Assertions.assertEquals(
            List.of(
                "Term frequency too large: 4294967295",
                "Collection frequency too large: 4294967295",
                "First document out of range: 4294967296",
                "Last chunk flag out of data",
                "Last document out of range: 4294967296",
                1
            ),
            seen,
            "Counts and documents above the range, truncated flag"
        );
    }

    @Test
    void walksPostingsOfChunk() {
        final PostingChunk chunk = PostingChunk.of(
            DocId.of(10), ByteReader.over(HexFormat.of().parseHex("0300020105"))
        );
        final List<Object> seen = new ArrayList<>(12);
        seen.add(chunk.doc().value());
        seen.add(chunk.wdf().value());
        seen.add(chunk.ended());
        seen.add(chunk.next());
        seen.add(chunk.doc().value());
        seen.add(chunk.wdf().value());
        seen.add(chunk.next());
        seen.add(chunk.doc().value());
        seen.add(chunk.wdf().value());
        seen.add(chunk.next());
        seen.add(chunk.ended());
        seen.add(chunk.doc().value());
        Assertions.assertEquals(
            List.of(10, 3, false, true, 11, 2, true, 13, 5, false, true, 13),
            seen,
            "Gaps one below the distance, ended after the last posting"
        );
    }

    @Test
    void forwardsWithinChunk() {
        final ByteReader reader = ByteReader.over(
            HexFormat.of().parseHex("0300020105090a")
        );
        final PostingChunk chunk = PostingChunk.of(DocId.of(10), reader);
        Assertions.assertEquals(
            List.of(true, 10, 3, true, 13, 5, false, 23, 5, true),
            List.of(
                chunk.forward(DocId.of(9)), chunk.doc().value(),
                chunk.wdf().value(), chunk.forward(DocId.of(12)),
                chunk.doc().value(), chunk.wdf().value(),
                chunk.forward(DocId.of(30)), chunk.doc().value(),
                chunk.wdf().value(), reader.done()
            ),
            "At or after the target, stale frequency when run out"
        );
    }

    @Test
    void emptyChunkIsEnded() {
        final PostingChunk chunk = PostingChunk.none();
        Assertions.assertEquals(
            List.of(true, false, true, 0, 0),
            List.of(
                chunk.ended(), chunk.next(), chunk.ended(),
                chunk.doc().value(), chunk.wdf().value()
            ),
            "No postings at all"
        );
    }

    @Test
    void drainsRestOfChunk() {
        final ByteReader reader = ByteReader.over(
            HexFormat.of().parseHex("03000201")
        );
        final PostingChunk chunk = PostingChunk.of(DocId.of(10), reader);
        chunk.drain();
        Assertions.assertEquals(
            List.of(true, false, true),
            List.of(reader.done(), chunk.next(), chunk.ended()),
            "Nothing left to read"
        );
    }

    @Test
    void rejectsDocumentGapPastRange() {
        Assertions.assertEquals(
            "Document out of range: 4294967305",
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                PostingChunk.of(
                    DocId.of(10),
                    ByteReader.over(HexFormat.of().parseHex("03feffffff0f01"))
                )::next,
                "Gap too large"
            ).getMessage(),
            "Message"
        );
    }

    @Test
    void rejectsTruncatedPosting() {
        final PostingChunk chunk = PostingChunk.of(
            DocId.of(10), ByteReader.over(HexFormat.of().parseHex("0300"))
        );
        Assertions.assertEquals(
            "Within-document frequency out of data",
            Assertions.assertThrows(
                DatabaseCorruptError.class, () -> chunk.forward(DocId.of(11)),
                "Gap without a frequency"
            ).getMessage(),
            "Message"
        );
    }
}
