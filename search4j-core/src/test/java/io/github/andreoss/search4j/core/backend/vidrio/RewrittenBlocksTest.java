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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Writes the entries of tables written by the reference implementation
 * again, in key order into a new table at revision 1, and compares the
 * blocks and root information with the reference's.
 *
 * @since 0.1.0
 */
final class RewrittenBlocksTest {

    /**
     * Compression threshold of the document data and term list tables.
     */
    private static final int COMPRESS = 18;

    /**
     * Revision of the reference tables.
     */
    private static final RevisionNumber FIRST = new RevisionNumber(1L);

    @Test
    void rewritesPostingBlockByteForByte() throws IOException {
        Assertions.assertArrayEquals(
            RewrittenBlocksTest.block("postlist"),
            RewrittenBlocksTest.rewrite("postlist", 0).bytes(0L),
            "Posting block as the reference wrote it"
        );
    }

    @Test
    void rewritesTermListBlockByteForByte() throws IOException {
        Assertions.assertArrayEquals(
            RewrittenBlocksTest.block("termlist"),
            RewrittenBlocksTest.rewrite("termlist", RewrittenBlocksTest.COMPRESS)
                .bytes(0L),
            "Term list block as the reference wrote it"
        );
    }

    @Test
    void rewritesDocumentDataExceptDamagedSize() throws IOException {
        final byte[] want = RewrittenBlocksTest.block("docdata");
        final MemoryBlocks out = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        final TableWriter writer =
            TableWriter.create(out, RewrittenBlocksTest.COMPRESS);
        for (int doc = 1; doc <= 112; ++doc) {
            writer.add(
                DocDataKeys.of(DocId.of(doc)).data(), Trees.key("hello world")
            );
        }
        writer.commit(RewrittenBlocksTest.FIRST);
        final byte[] got = out.bytes(0L);
        final List<Integer> diffs = new ArrayList<>(2);
        for (int idx = 0; idx < got.length; ++idx) {
            if (got[idx] != want[idx]) {
                diffs.add(idx);
            }
        }
        Assertions.assertEquals(
            List.of(253, 254), diffs,
            "Only the size field the reference test data damaged differs"
        );
    }

    @Test
    void writesPostingRootInformation() throws IOException {
        Assertions.assertEquals(
            "000201010006010100000000",
            HexFormat.of().formatHex(
                RewrittenBlocksTest.state("postlist", 0).bytes()
            ),
            "Posting root information of the reference version record"
        );
    }

    @Test
    void writesTermListRootInformation() throws IOException {
        Assertions.assertEquals(
            "000270011206010100000000",
            HexFormat.of().formatHex(
                RewrittenBlocksTest.state(
                    "termlist", RewrittenBlocksTest.COMPRESS
                ).bytes()
            ),
            "Term list root information of the reference version record"
        );
    }

    @Test
    void rejectsAdditionToBlockWithOversizedItem() throws IOException {
        final TableWriter writer = RewrittenBlocksTest.damaged("docdata");
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> writer.add(
                DocDataKeys.of(DocId.of(113)).data(), Trees.key("hello world")
            ),
            "Item extends outside the block"
        );
    }

    @Test
    void rejectsAdditionToBlockWithOverlappingItem() throws IOException {
        final TableWriter writer = RewrittenBlocksTest.damaged("docdata2");
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> writer.add(
                DocDataKeys.of(DocId.of(113)).data(), Trees.key("hello world")
            ),
            "Items overlap the directory"
        );
    }

    private static TableWriter damaged(final String table) throws IOException {
        return TableWriter.open(
            RewrittenBlocksTest.source(table),
            new TableState(
                RewrittenBlocksTest.root(), new EntryCount(112L), true,
                RewrittenBlocksTest.COMPRESS,
                new FreeListInfo(
                    RewrittenBlocksTest.FIRST, new BlockNumber(1L),
                    FreeListMark.START, FreeListMark.START
                )
            )
        );
    }

    private static TableState state(final String table, final int compress)
        throws IOException {
        return RewrittenBlocksTest.copy(
            table, new MemoryBlocks(new HashMap<>(), BlockSize.MIN), compress
        );
    }

    private static MemoryBlocks rewrite(final String table, final int compress)
        throws IOException {
        final MemoryBlocks out = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        RewrittenBlocksTest.copy(table, out, compress);
        return out;
    }

    private static TableState copy(
        final String table, final MemoryBlocks out, final int compress
    ) throws IOException {
        final TableWriter writer = TableWriter.create(out, compress);
        final VidrioTable src = VidrioTable.over(
            RewrittenBlocksTest.source(table), RewrittenBlocksTest.root()
        );
        final TableCursor cursor = src.cursor();
        cursor.rewind();
        while (cursor.next()) {
            writer.add(cursor.key().data(), cursor.tag());
        }
        return writer.commit(RewrittenBlocksTest.FIRST);
    }

    private static TableRoot root() {
        return new TableRoot(
            new BlockNumber(0L), 0, BlockSize.MIN, RewrittenBlocksTest.FIRST,
            false
        );
    }

    private static MemoryBlocks source(final String table) throws IOException {
        final MemoryBlocks src = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        src.write(new BlockNumber(0L), RewrittenBlocksTest.block(table));
        return src;
    }

    private static byte[] block(final String table) throws IOException {
        try (
            InputStream input = RewrittenBlocksTest.class.getResourceAsStream(
                String.format("oversized/%s.bin", table)
            )
        ) {
            return input.readAllBytes();
        }
    }
}
