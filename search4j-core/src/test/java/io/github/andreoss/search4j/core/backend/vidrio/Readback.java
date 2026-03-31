/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;

/**
 * Posting lists read back from a committed table, as text.
 *
 * @since 0.1.0
 */
final class Readback {

    /**
     * Utility class, no instances.
     */
    private Readback() {
    }

    /**
     * Postings of a term read from a table.
     *
     * @param table Table
     * @param term Term
     * @return Document and frequency pairs in document order
     */
    static List<String> postings(final VidrioTable table, final String term) {
        final List<String> result = new ArrayList<>(0);
        final VidrioPostList list = VidrioPostList.open(table, term);
        list.next();
        while (!list.atEnd()) {
            result.add(Readback.posting(list.docId(), list.wdf()));
            list.next();
        }
        return result;
    }

    /**
     * Lengths of every document read from a table.
     *
     * @param table Table
     * @return Document and length pairs in document order
     */
    static List<String> lengths(final VidrioTable table) {
        final List<String> result = new ArrayList<>(0);
        final VidrioAllDocsPostList list =
            VidrioAllDocsPostList.open(table, TermCount.ZERO);
        final PostingTable lookup = PostingTable.of(table);
        list.next();
        while (!list.atEnd()) {
            result.add(
                Readback.posting(list.docId(), lookup.docLength(list.docId()))
            );
            list.next();
        }
        return result;
    }

    /**
     * Chunk keys of a term in a table.
     *
     * @param table Table
     * @param term Term
     * @return First document of every chunk after the first
     */
    static List<DocId> chunks(final VidrioTable table, final String term) {
        final List<DocId> result = new ArrayList<>(0);
        final byte[] name = PostListKeys.bytes(term);
        final TableCursor cursor = table.cursor();
        cursor.seek(PostListKeys.first(name));
        boolean more = cursor.next();
        while (more) {
            final ChunkKey key = ChunkKey.of(cursor.key());
            more = key.matches(name);
            if (more) {
                result.add(key.start());
                more = cursor.next();
            }
        }
        return result;
    }

    /**
     * A posting as text.
     *
     * @param doc Document
     * @param wdf Frequency
     * @return Text
     */
    static String posting(final DocId doc, final TermCount wdf) {
        return String.format("%s:%s", doc, wdf);
    }

    /**
     * A committed table of entries given as hexadecimal.
     *
     * @param pairs Key and tag of every entry, in hexadecimal
     * @return Table
     */
    static VidrioTable crafted(final String... pairs) {
        final MemoryBlocks blocks =
            new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        final TableWriter writer = TableWriter.create(blocks, 0);
        for (int idx = 0; idx < pairs.length; idx += 2) {
            writer.add(
                HexFormat.of().parseHex(pairs[idx]),
                HexFormat.of().parseHex(pairs[idx + 1])
            );
        }
        return VidrioTable.over(
            blocks, writer.commit(new RevisionNumber(1L)).root()
        );
    }
}
