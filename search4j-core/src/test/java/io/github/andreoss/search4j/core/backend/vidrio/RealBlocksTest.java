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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Decodes blocks of table files written by the reference implementation.
 *
 * @since 0.1.0
 */
final class RealBlocksTest {

    @Test
    void decodesDocumentDataHeader() throws IOException {
        Assertions.assertEquals(
            new BlockHeader(new RevisionNumber(1L), 0, 16, 16, 237),
            BlockHeader.parse(RealBlocksTest.block("docdata")),
            "Document data block header"
        );
    }

    @Test
    void decodesPostingHeader() throws IOException {
        Assertions.assertEquals(
            new BlockHeader(new RevisionNumber(1L), 0, 0x0705, 0x0705, 15),
            BlockHeader.parse(RealBlocksTest.block("postlist")),
            "Posting block header"
        );
    }

    @Test
    void endsWithRootItem() throws IOException {
        for (final String table : new String[] {"docdata", "termlist"}) {
            final byte[] block = RealBlocksTest.block(table);
            Assertions.assertEquals(
                LeafItem.root(),
                RealBlocksTest.items(block, 1).get(0),
                table
            );
        }
    }

    @Test
    void decodesDocumentData() throws IOException {
        final List<LeafItem> items =
            RealBlocksTest.items(RealBlocksTest.block("docdata"), 112);
        final LeafItem item = items.get(1);
        Assertions.assertEquals(
            List.of(
                112, 16, DocId.of(1), "hello world", true, true, false
            ),
            List.of(
                items.size(), item.size(), DocDataKeys.parse(item.key()),
                new String(item.chunk(), StandardCharsets.US_ASCII),
                item.part().first(), item.part().last(),
                item.part().compressed()
            ),
            "First document data item"
        );
    }

    @Test
    void ordersDirectoryByKey() throws IOException {
        for (final String table : new String[] {"docdata", "termlist"}) {
            final List<LeafItem> items =
                RealBlocksTest.items(RealBlocksTest.block(table), 100);
            for (int idx = 1; idx < items.size(); ++idx) {
                Assertions.assertTrue(
                    items.get(idx - 1).order(items.get(idx)) < 0,
                    String.format("%s item %d", table, idx)
                );
            }
        }
    }

    @Test
    void detectsOversizedItem() throws IOException {
        final byte[] block = RealBlocksTest.block("docdata");
        final int offset = Short.toUnsignedInt(
            ByteBuffer.wrap(block).getShort(
                BlockHeader.parse(block).entryOffset(112)
            )
        );
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> LeafItem.parse(block, offset),
            "Size field past block end"
        );
    }

    @Test
    void reencodesItemsByteForByte() throws IOException {
        final byte[] block = RealBlocksTest.block("termlist");
        final BlockHeader header = BlockHeader.parse(block);
        final ByteBuffer buf = ByteBuffer.wrap(block);
        for (int idx = 0; idx < header.entries(); ++idx) {
            final int offset = Short.toUnsignedInt(
                buf.getShort(header.entryOffset(idx))
            );
            final LeafItem item = LeafItem.parse(block, offset);
            Assertions.assertArrayEquals(
                Arrays.copyOfRange(block, offset, offset + item.size()),
                item.bytes(),
                String.format("Item %d", idx)
            );
        }
    }

    private static List<LeafItem> items(final byte[] block, final int count) {
        final BlockHeader header = BlockHeader.parse(block);
        final ByteBuffer buf = ByteBuffer.wrap(block);
        final List<LeafItem> items = new ArrayList<>(count);
        for (int idx = 0; idx < count; ++idx) {
            items.add(
                LeafItem.parse(
                    block,
                    Short.toUnsignedInt(buf.getShort(header.entryOffset(idx)))
                )
            );
        }
        return items;
    }

    private static InputStream resource(final String table) {
        return RealBlocksTest.class.getResourceAsStream(
            String.format("oversized/%s.bin", table)
        );
    }

    private static byte[] block(final String table) throws IOException {
        try (InputStream input = RealBlocksTest.resource(table)) {
            Assertions.assertNotNull(input, table);
            final byte[] data = input.readAllBytes();
            Assertions.assertEquals(
                BlockSize.MIN.bytes(), data.length, "One minimum block"
            );
            return data;
        }
    }
}
