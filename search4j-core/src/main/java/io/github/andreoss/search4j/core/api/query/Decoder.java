/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.error.SerialisationError;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.Map;

/**
 * Reader of the reference wire format of a query.
 *
 * <p>Nodes are built as the reference builds them while reading: leaves
 * directly, without the simplifications of the factory methods, and
 * branches through the subquery rules of their operator but without the
 * positional check. A subquery missing at the end of the data is a
 * match-nothing query, as in the reference.</p>
 *
 * @since 0.1.0
 */
final class Decoder {

    /**
     * Reference message of a leaf cut short.
     */
    private static final String LEAF = "not enough data";

    /**
     * Mask of a slot number in the first byte of a value condition.
     */
    private static final int SLOT = 0x0F;

    /**
     * The data being read.
     */
    private final Cursor cursor;

    /**
     * Creates the reader.
     *
     * @param cursor The data being read
     */
    private Decoder(final Cursor cursor) {
        this.cursor = cursor;
    }

    /**
     * Unserialises a query.
     *
     * @param data Bytes of exactly one query
     * @return The query, match-nothing for no bytes
     */
    static Query decode(final byte[] data) {
        final Decoder decoder = new Decoder(new Cursor(data));
        final Query query = decoder.query();
        if (!decoder.cursor.exhausted()) {
            throw new SerialisationError("Extra data after serialised Query");
        }
        return query;
    }

    private Query query() {
        final Query result;
        if (this.cursor.exhausted()) {
            result = Query.MATCH_NOTHING;
        } else {
            final int first = this.cursor.next();
            result = switch (first >> 5) {
                case 4, 5, 6, 7 -> this.branch(first);
                case 2, 3 -> new Query(this.term(first));
                case 1 -> new Query(this.value(first));
                default -> this.other(first);
            };
        }
        return result;
    }

    private Query branch(final int first) {
        long count = first & 0x07;
        if (count == 0) {
            count = Math.min(this.cursor.uint(64), Long.MAX_VALUE - 8L) + 8L;
        }
        final int code = first >> 3 & 0x0F;
        long parameter = 0L;
        if (code >= 13) {
            parameter = this.cursor.uint(32);
        }
        final Branch branch = Branch.of(
            Decoder.operator(code), (int) parameter
        );
        long left = count;
        while (left > 0L) {
            if (this.cursor.exhausted()) {
                branch.add(Query.MATCH_NOTHING, false);
                left = 0L;
            } else {
                branch.add(this.query(), false);
                --left;
            }
        }
        return branch.done();
    }

    private TermNode term(final int first) {
        long length = first & 0x0F;
        if (length == 0) {
            length = Math.min(this.cursor.uint(64), Integer.MAX_VALUE) + 16L;
        }
        if (length > this.cursor.remaining()) {
            throw new SerialisationError("Not enough data");
        }
        final String text = this.cursor.text((int) length);
        final int code = first >> 4 & 0x03;
        long wqf = 0L;
        if (code > 0) {
            wqf = 1L;
        }
        if (code == 3) {
            wqf = this.cursor.uint(32);
        }
        long pos = 0L;
        if (code >= 2) {
            pos = this.cursor.uint(32);
        }
        return new TermNode(
            text, new TermCount(Decoder.small(wqf)), new TermPos(Decoder.small(pos))
        );
    }

    private Node value(final int first) {
        long number = first & Decoder.SLOT;
        if (number == Decoder.SLOT) {
            number = (this.cursor.uint(32) + Decoder.SLOT) & ValueNo.BAD;
        }
        final ValueNo slot = new ValueNo(number);
        final String begin = this.cursor.string();
        final Node node;
        if ((first & 0x10) == 0) {
            final String end = this.cursor.string();
            if (begin.isEmpty()) {
                node = new BoundNode(QueryOp.VALUE_LE, slot, end);
            } else {
                node = new RangeNode(slot, begin, end);
            }
        } else {
            node = new BoundNode(QueryOp.VALUE_GE, slot, begin);
        }
        return node;
    }

    private Query other(final int first) {
        return switch (first & 0x1F) {
            case 0x00 -> Query.invalid();
            case 0x0A -> new Query(new EditNode(this.edit()));
            case 0x0B -> new Query(new WildcardNode(this.wildcard()));
            case 0x0C -> throw new SerialisationError(
                String.format(
                    "PostingSource %s not registered",
                    this.cursor.string(Decoder.LEAF, Decoder.LEAF)
                )
            );
            case 0x0D -> this.scale();
            case 0x0E -> new Query(
                new TermNode(
                    "",
                    new TermCount(Decoder.small(this.leaf(32))),
                    new TermPos(Decoder.small(this.leaf(32)))
                )
            );
            case 0x0F -> Query.MATCH_ALL;
            default -> throw new SerialisationError(
                String.format("Unknown Query serialisation: %d", first)
            );
        };
    }

    private EditDistance edit() {
        return Decoder.distance(
            this.maximum(), this.cursor.next(), this.cursor.next(),
            Decoder.small(this.leaf(32)), Decoder.small(this.leaf(64)),
            this.cursor.string(Decoder.LEAF, Decoder.LEAF)
        );
    }

    private Wildcard wildcard() {
        return Decoder.pattern(
            this.maximum(), this.cursor.next(), this.cursor.next(),
            this.cursor.string(Decoder.LEAF, Decoder.LEAF)
        );
    }

    private TermCount maximum() {
        final TermCount max = new TermCount(Decoder.small(this.leaf(32)));
        if (this.cursor.remaining() < 2) {
            throw new SerialisationError(Decoder.LEAF);
        }
        return max;
    }

    private Query scale() {
        final double factor = this.cursor.real();
        final Query scaled = this.query();
        final ScaleNode node = new ScaleNode(factor, scaled);
        if (scaled.empty()) {
            throw new SerialisationError(Cursor.UNSUPPORTED);
        }
        return new Query(node);
    }

    private long leaf(final int bits) {
        return this.cursor.uint(bits, Decoder.LEAF, Decoder.LEAF);
    }

    private static QueryOp operator(final int code) {
        return Encoder.CODES.entrySet().stream()
            .filter(entry -> entry.getValue() == code)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseThrow(Decoder::unknown);
    }

    private static EditDistance distance(final TermCount max, final int flags,
        final int combiner, final int edits, final int prefix,
        final String target) {
        return new EditDistance(
            target, new Expansion(max, Limits.limit(flags)),
            Limits.combiner(combiner), edits, prefix
        );
    }

    private static Wildcard pattern(final TermCount max, final int flags,
        final int combiner, final String pattern) {
        return new Wildcard(
            pattern, new Expansion(max, Limits.limit(flags)),
            Limits.glob(flags), Limits.combiner(combiner)
        );
    }

    private static SerialisationError unknown() {
        return new SerialisationError("Unknown multi-way branch Query operator");
    }

    private static int small(final long value) {
        if (value > Integer.MAX_VALUE) {
            throw new SerialisationError(Cursor.UNSUPPORTED);
        }
        return (int) value;
    }
}
