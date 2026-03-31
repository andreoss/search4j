/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.ser.Varints;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Writer of the reference wire format of a query.
 *
 * <p>A branch starts with {@code 1ccccnnn} (operator code, subquery count
 * or zero when the count follows), a term with {@code 01ccLLLL} (frequency
 * and position code, length or zero when the length follows), a value
 * condition with {@code 001tssss} (bound kind, slot or 15 when the slot
 * follows), any other node with {@code 000ttttt}.</p>
 *
 * @since 0.1.0
 */
final class Encoder {

    /**
     * Operator codes of the branches in their first byte.
     */
    static final Map<QueryOp, Integer> CODES = Map.ofEntries(
        Map.entry(QueryOp.AND, 0), Map.entry(QueryOp.OR, 1),
        Map.entry(QueryOp.AND_NOT, 2), Map.entry(QueryOp.XOR, 3),
        Map.entry(QueryOp.AND_MAYBE, 4), Map.entry(QueryOp.FILTER, 5),
        Map.entry(QueryOp.SYNONYM, 6), Map.entry(QueryOp.MAX, 7),
        Map.entry(QueryOp.ELITE_SET, 13), Map.entry(QueryOp.NEAR, 14),
        Map.entry(QueryOp.PHRASE, 15)
    );

    /**
     * Bytes written so far.
     */
    private final ByteArrayOutputStream out;

    /**
     * Creates an empty writer.
     */
    private Encoder() {
        this.out = new ByteArrayOutputStream();
    }

    /**
     * Serialises a query.
     *
     * @param query The query
     * @return Its bytes, none for a match-nothing query
     */
    static byte[] encode(final Query query) {
        final Encoder encoder = new Encoder();
        if (!query.empty()) {
            encoder.node(query.node());
        }
        return encoder.out.toByteArray();
    }

    /**
     * Operator code of a branch in the first byte.
     *
     * @param type The operator
     * @return Code from 0 to 15
     */
    static int code(final QueryOp type) {
        return Encoder.CODES.get(type);
    }

    private void node(final Node node) {
        if (node instanceof BranchNode branch) {
            this.branch(branch);
        } else if (node instanceof TermNode term) {
            this.term(term);
        } else if (node instanceof ScaleNode scale) {
            this.scale(scale);
        } else {
            this.condition(node);
        }
    }

    private void condition(final Node node) {
        if (node instanceof BoundNode bound) {
            this.bound(bound);
        } else if (node instanceof RangeNode range) {
            this.range(range);
        } else if (node instanceof WildcardNode wildcard) {
            this.wildcard(wildcard.spec());
        } else if (node instanceof EditNode edit) {
            this.edit(edit.spec());
        } else {
            this.out.write(0);
        }
    }

    private void term(final TermNode node) {
        final byte[] text = node.term().getBytes(StandardCharsets.UTF_8);
        final int wqf = node.wqf().value();
        final int pos = node.pos().value();
        if (text.length == 0) {
            if (wqf == 1 && pos == 0) {
                this.out.write(0x0F);
            } else {
                this.out.write(0x0E);
                this.uint(wqf);
                this.uint(pos);
            }
        } else {
            final int code = Encoder.frequency(wqf, pos);
            this.head(0x40 | code << 4, text.length);
            this.out.writeBytes(text);
            if (code == 3) {
                this.uint(wqf);
            }
            if (code >= 2) {
                this.uint(pos);
            }
        }
    }

    private void scale(final ScaleNode node) {
        this.out.write(0x0D);
        long bits = Double.doubleToRawLongBits(node.factor());
        for (int item = 0; item < 8; ++item) {
            this.out.write((int) (bits & 0xFF));
            bits >>>= 8;
        }
        this.node(node.scaled().node());
    }

    private void bound(final BoundNode node) {
        if (node.type() == QueryOp.VALUE_GE) {
            this.slot(0x30, node.slot());
        } else {
            this.slot(0x20, node.slot());
            this.out.write(0);
        }
        this.string(node.limit());
    }

    private void range(final RangeNode node) {
        this.slot(0x20, node.slot());
        this.string(node.lower());
        this.string(node.upper());
    }

    private void wildcard(final Wildcard spec) {
        this.out.write(0x0B);
        this.uint(spec.expansion().max().value());
        this.out.write(spec.glob().code() | Limits.code(spec.expansion().limit()));
        this.out.write(spec.combiner().code());
        this.string(spec.pattern());
    }

    private void edit(final EditDistance spec) {
        this.out.write(0x0A);
        this.uint(spec.expansion().max().value());
        this.out.write(Limits.code(spec.expansion().limit()));
        this.out.write(spec.combiner().code());
        this.uint(spec.edits());
        this.uint(spec.prefix());
        this.string(spec.target());
    }

    private void branch(final BranchNode node) {
        final int code = Encoder.code(node.type());
        final int count = node.subqueries().size();
        final int first = 0x80 | code << 3;
        if (count < 8) {
            this.out.write(first | count);
        } else {
            this.out.write(first);
            this.uint(count - 8);
        }
        if (code >= 13) {
            this.uint(node.parameter());
        }
        for (final Query sub : node.subqueries()) {
            this.node(sub.node());
        }
    }

    private void slot(final int type, final ValueNo slot) {
        if (slot.value() < 15) {
            this.out.write(type | (int) slot.value());
        } else {
            this.out.write(type | 15);
            this.uint(slot.value() - 15);
        }
    }

    private void head(final int type, final int length) {
        if (length >= 16) {
            this.out.write(type);
            this.uint(length - 16);
        } else {
            this.out.write(type | length);
        }
    }

    private void string(final String text) {
        final byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        this.uint(bytes.length);
        this.out.writeBytes(bytes);
    }

    private void uint(final long value) {
        this.out.writeBytes(Varints.pack(value));
    }

    private static int frequency(final int wqf, final int pos) {
        final int code;
        if (wqf == 1 && pos == 0) {
            code = 1;
        } else if (wqf == 1) {
            code = 2;
        } else if (wqf > 1 || pos > 0) {
            code = 3;
        } else {
            code = 0;
        }
        return code;
    }
}
