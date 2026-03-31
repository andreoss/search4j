/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file, the posting lists and the term
 * lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.InvariantError;
import io.github.andreoss.search4j.core.ser.Varints;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.io.ByteArrayOutputStream;
import java.util.Optional;

/**
 * Writer of one posting list chunk, replacing the chunk it was opened on.
 *
 * <p>Postings are appended in document order; a chunk grown to the
 * threshold is written out and a new one started under its own key. On
 * flush an emptied chunk is removed, with the neighbouring chunks
 * adjusted, and a chunk whose first document changed is filed under its
 * new key.</p>
 *
 * @since 0.1.0
 */
final class ChunkWriter {

    /**
     * Table written.
     */
    private final TableWriter table;

    /**
     * Term bytes.
     */
    private final byte[] term;

    /**
     * Postings of the chunk.
     */
    private final ByteArrayOutputStream chunk;

    /**
     * Key of the chunk replaced, empty for a new list.
     */
    private byte[] origin;

    /**
     * Whether the chunk is the first of its list.
     */
    private boolean head;

    /**
     * Whether the chunk is the last of its list.
     */
    private boolean tail;

    /**
     * Whether a posting was appended.
     */
    private boolean started;

    /**
     * First document of the chunk.
     */
    private DocId first;

    /**
     * Last document appended.
     */
    private DocId current;

    /**
     * Creates a writer over a chunk.
     *
     * @param table Table written
     * @param term Term bytes
     * @param origin Key of the chunk replaced, empty for a new list
     * @param head Whether the chunk is the first of its list
     * @param tail Whether the chunk is the last of its list
     */
    ChunkWriter(
        final TableWriter table, final byte[] term, final byte[] origin,
        final boolean head, final boolean tail
    ) {
        this.table = table;
        this.term = term.clone();
        this.chunk = new ByteArrayOutputStream();
        this.origin = origin.clone();
        this.head = head;
        this.tail = tail;
        this.first = DocId.NONE;
        this.current = DocId.NONE;
    }

    /**
     * Appends a posting.
     *
     * @param doc Document, after every document appended
     * @param wdf Frequency in the document
     */
    void append(final DocId doc, final TermCount wdf) {
        if (this.started) {
            if (doc.compareTo(this.current) <= 0) {
                throw new InvariantError("Postings appended out of order");
            }
            if (this.chunk.size() >= 2000) {
                this.split(doc);
            } else {
                this.chunk.writeBytes(
                    Varints.pack((long) doc.value() - this.current.value() - 1L)
                );
            }
        } else {
            this.started = true;
            this.first = doc;
        }
        this.current = doc;
        this.chunk.writeBytes(Varints.pack(wdf.value()));
    }

    /**
     * Takes over the encoded postings of the chunk replaced.
     *
     * @param start First document of the chunk
     * @param last Last document of the chunk
     * @param rest Encoded postings after the chunk header
     */
    void raw(final DocId start, final DocId last, final byte[] rest) {
        this.first = start;
        this.current = last;
        if (rest.length > 0) {
            this.chunk.writeBytes(rest);
            this.started = true;
        }
    }

    /**
     * Writes the chunk to the table.
     */
    void flush() {
        if (this.started) {
            this.write();
        } else {
            this.delete();
        }
    }

    private void split(final DocId doc) {
        final boolean keep = this.tail;
        this.tail = false;
        this.flush();
        this.tail = keep;
        this.head = false;
        this.first = doc;
        this.chunk.reset();
        this.origin = PostListKeys.chunk(this.term, doc);
    }

    private void write() {
        if (this.head) {
            final byte[] key = PostListKeys.first(this.term);
            final Optional<byte[]> tag = this.table.get(key);
            if (tag.isEmpty()) {
                throw new DatabaseCorruptError(
                    "First chunk of the posting list has disappeared"
                );
            }
            final FirstHeader found = FirstHeader.read(ByteReader.over(tag.get()));
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.writeBytes(new FirstHeader(found.freqs(), this.first).bytes());
            out.writeBytes(this.body());
            this.table.add(key, out.toByteArray());
        } else {
            final ChunkKey key = ChunkKey.of(ItemKey.of(this.origin));
            if (!key.matches(this.term)) {
                throw new DatabaseCorruptError(
                    "Have invalid key writing to posting list"
                );
            }
            byte[] name = this.origin;
            if (!key.start().equals(this.first)) {
                name = PostListKeys.chunk(this.term, this.first);
                this.table.delete(this.origin);
            }
            this.table.add(name, this.body());
        }
    }

    private byte[] body() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(
            new ChunkHeader(this.tail, this.current).bytes(this.first)
        );
        out.writeBytes(this.chunk.toByteArray());
        return out.toByteArray();
    }

    private void delete() {
        if (this.head) {
            if (this.tail) {
                this.table.delete(this.origin);
            } else {
                this.promote();
            }
        } else {
            this.table.delete(this.origin);
            if (this.tail) {
                this.terminate();
            }
        }
    }

    private void promote() {
        final TableCursor cursor = this.table.cursor();
        if (!cursor.exact(this.origin)) {
            throw new DatabaseCorruptError(
                "The key we're working on has disappeared"
            );
        }
        final FirstHeader found = FirstHeader.read(ByteReader.over(cursor.tag()));
        if (!cursor.next()) {
            throw new DatabaseCorruptError("Expected another key but found none");
        }
        final ChunkKey key = ChunkKey.of(cursor.key());
        if (!key.matches(this.term)) {
            throw new DatabaseCorruptError(
                "Expected another key with the same term name but found a different one"
            );
        }
        final DocId start = key.start();
        final ByteReader reader = ByteReader.over(cursor.tag());
        final ChunkHeader next = ChunkHeader.read(reader, start);
        final byte[] rest = reader.rest();
        this.table.delete(cursor.key().data());
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new FirstHeader(found.freqs(), start).bytes());
        out.writeBytes(next.bytes(start));
        out.writeBytes(rest);
        this.table.add(this.origin, out.toByteArray());
    }

    private void terminate() {
        final TableCursor cursor = this.table.cursor();
        if (cursor.seek(this.origin)) {
            throw new DatabaseCorruptError("Key not deleted as we expected");
        }
        final ChunkKey key = ChunkKey.of(cursor.key());
        if (!key.matches(this.term)) {
            throw new DatabaseCorruptError(
                "Couldn't find chunk before deleted chunk"
            );
        }
        final byte[] tag = cursor.tag();
        final ByteReader reader = ByteReader.over(tag);
        final DocId start;
        if (key.first()) {
            start = FirstHeader.read(reader).first();
        } else {
            start = key.start();
        }
        final int from = reader.offset();
        final ChunkHeader found = ChunkHeader.read(reader, start);
        final int until = reader.offset();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag, 0, from);
        out.writeBytes(new ChunkHeader(true, found.end()).bytes(start));
        out.write(tag, until, tag.length - until);
        this.table.add(cursor.key().data(), out.toByteArray());
    }
}
