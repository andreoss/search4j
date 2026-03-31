/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table and the version file.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.TermFrequencies;
import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.InvariantError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;

/**
 * Merges pending changes into a posting list table being written.
 *
 * @since 0.1.0
 */
final class PostingWriter {

    /**
     * Term of the document lengths.
     */
    private static final byte[] LENGTHS = {};

    /**
     * Table written.
     */
    private final TableWriter table;

    /**
     * Creates the merge over a table.
     *
     * @param table Table written
     */
    PostingWriter(final TableWriter table) {
        this.table = table;
    }

    /**
     * Merges the changes of a term: its frequencies in the first chunk,
     * then every posting changed.
     *
     * @param name Term text
     * @param changes Changes, at least one posting
     */
    void merge(final String name, final PostingChanges changes) {
        if (changes.entries().isEmpty()) {
            throw new InvalidArgumentError("No posting changes to merge");
        }
        final byte[] term = PostListKeys.bytes(name);
        final byte[] key = PostListKeys.first(term);
        final byte[] tag = this.table.get(key).orElse(new byte[0]);
        final ByteReader reader = ByteReader.over(tag);
        FirstHeader head = new FirstHeader(TermFrequencies.NONE, DocId.of(1));
        ChunkHeader chunk = new ChunkHeader(true, DocId.of(1));
        if (tag.length > 0) {
            head = FirstHeader.read(reader);
            chunk = ChunkHeader.read(reader, head.first());
        }
        final long docs =
            head.freqs().termFrequency().value() + changes.termDelta();
        if (docs == 0L) {
            this.drop(key, term, chunk.last());
        } else {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.writeBytes(
                new FirstHeader(
                    new TermFrequencies(
                        PostingWriter.count(docs, "Term frequency"),
                        PostingWriter.count(
                            head.freqs().collection().value()
                                + changes.collectionDelta(),
                            "Collection frequency"
                        )
                    ),
                    head.first()
                ).bytes()
            );
            out.writeBytes(chunk.bytes(head.first()));
            out.writeBytes(reader.rest());
            this.table.add(key, out.toByteArray());
            this.apply(term, changes.entries(), false);
        }
    }

    /**
     * Merges changes of document lengths, creating the list when absent.
     *
     * @param lengths New length per document, empty for a removed one
     */
    void mergeLengths(final PostingChanges lengths) {
        if (!lengths.entries().isEmpty()) {
            final byte[] key = PostListKeys.first(PostingWriter.LENGTHS);
            if (!this.table.contains(key)) {
                this.table.add(key, new byte[] {0, 0, 0, '1', 0});
            }
            this.apply(PostingWriter.LENGTHS, lengths.entries(), true);
        }
    }

    private void apply(
        final byte[] term,
        final NavigableMap<DocId, Optional<TermCount>> entries,
        final boolean adding
    ) {
        ChunkEdit edit = this.locate(term, entries.firstKey(), adding);
        for (final Map.Entry<DocId, Optional<TermCount>> entry
            : entries.entrySet()) {
            final DocId doc = entry.getKey();
            edit.reach(doc);
            while (edit.exhausted() && doc.value() > edit.max()) {
                edit.flush();
                edit = this.locate(term, doc, false);
                edit.reach(doc);
            }
            if (entry.getValue().isPresent()) {
                edit.append(doc, entry.getValue().get());
            }
        }
        edit.finish();
    }

    private ChunkEdit locate(
        final byte[] term, final DocId doc, final boolean adding
    ) {
        final TableCursor cursor = this.table.cursor();
        cursor.seek(PostListKeys.chunk(term, doc));
        final ChunkKey key = ChunkKey.of(cursor.key());
        final ChunkEdit result;
        if (key.matches(term)) {
            result = this.found(cursor, key, term, doc);
        } else {
            if (!adding) {
                throw new DatabaseCorruptError(
                    String.format(
                        "Attempted to delete or modify an entry in a non-existent posting list for %s",
                        new String(term, StandardCharsets.UTF_8)
                    )
                );
            }
            result = new ChunkEdit(
                PostingChunk.none(),
                new ChunkWriter(this.table, term, new byte[0], true, true),
                ChunkEdit.UNBOUNDED
            );
        }
        return result;
    }

    private ChunkEdit found(
        final TableCursor cursor, final ChunkKey key, final byte[] term,
        final DocId doc
    ) {
        final ByteReader reader = ByteReader.over(cursor.tag());
        final DocId start;
        if (key.first()) {
            start = FirstHeader.read(reader).first();
        } else {
            start = key.start();
        }
        final ChunkHeader head = ChunkHeader.read(reader, start);
        final ChunkWriter writer = new ChunkWriter(
            this.table, term, cursor.key().data(), key.first(), head.last()
        );
        final PostingChunk source;
        if (doc.compareTo(head.end()) > 0) {
            writer.raw(start, head.end(), reader.rest());
            source = PostingChunk.none();
        } else {
            source = PostingChunk.of(start, reader);
        }
        long max = ChunkEdit.UNBOUNDED;
        if (!head.last()) {
            if (!cursor.next()) {
                throw new DatabaseCorruptError(
                    "Expected another key but found none"
                );
            }
            final ChunkKey next = ChunkKey.of(cursor.key());
            if (!next.matches(term)) {
                throw new DatabaseCorruptError(
                    "Expected another key with the same term name but found a different one"
                );
            }
            max = next.start().value() - 1L;
        }
        return new ChunkEdit(source, writer, max);
    }

    private void drop(final byte[] key, final byte[] term, final boolean last) {
        if (last) {
            this.table.delete(key);
        } else {
            final TableCursor cursor = this.table.cursor();
            final List<byte[]> keys = new ArrayList<>(1);
            if (cursor.exact(key)) {
                keys.add(key);
                while (cursor.next() && ChunkKey.of(cursor.key()).matches(term)) {
                    keys.add(cursor.key().data());
                }
            }
            for (final byte[] item : keys) {
                this.table.delete(item);
            }
        }
    }

    private static TermCount count(final long value, final String what) {
        if (value < 0L || value > Integer.MAX_VALUE) {
            throw new InvariantError(
                String.format("%s out of range: %d", what, value)
            );
        }
        return new TermCount((int) value);
    }
}
