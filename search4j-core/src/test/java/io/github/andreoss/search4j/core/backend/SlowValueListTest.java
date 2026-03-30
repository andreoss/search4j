/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the backend contract and support types.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.backend.inmemory.InMemoryShard;
import io.github.andreoss.search4j.core.backend.list.ValueList;
import io.github.andreoss.search4j.core.error.DocNotFoundError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link SlowValueList}.
 *
 * <p>The fixture holds value "b" in document 1, deletes document 2, has no
 * value in document 3 and value "d" in document 4.</p>
 *
 * @since 0.1.0
 */
final class SlowValueListTest {

    /**
     * The slot read.
     */
    private static final ValueNo SLOT = new ValueNo(1L);

    @Test
    void walksDocumentsWithValue() {
        final ValueList list = SlowValueListTest.list();
        final List<String> seen = new ArrayList<>(0);
        seen.add(SlowValueListTest.at(list));
        for (int step = 0; step < 3; step += 1) {
            list.next();
            seen.add(SlowValueListTest.at(list));
        }
        Assertions.assertEquals(
            List.of("0=", "1=b", "4=d", "end"), seen, "Holders in order"
        );
    }

    @Test
    void endsAtOnceOnEmptyShard() {
        Assertions.assertTrue(
            new SlowValueList(new InMemoryShard(), SlowValueListTest.SLOT)
                .atEnd(),
            "Nothing to read"
        );
    }

    @Test
    void staysEndedOnEmptyShard() {
        final ValueList list = new SlowValueList(
            new InMemoryShard(), SlowValueListTest.SLOT
        );
        list.next();
        Assertions.assertTrue(list.atEnd(), "Still ended");
    }

    @Test
    void readsOwnSlot() {
        Assertions.assertEquals(
            SlowValueListTest.SLOT, SlowValueListTest.list().slot(), "Slot"
        );
    }

    @Test
    void skipsForwardOnly() {
        final ValueList list = SlowValueListTest.list();
        final List<String> seen = new ArrayList<>(0);
        for (final int target : new int[] {2, 1, 4}) {
            list.skipTo(new DocId(target));
            seen.add(SlowValueListTest.at(list));
        }
        Assertions.assertEquals(
            List.of("4=d", "4=d", "4=d"), seen, "Next holder, never back"
        );
    }

    @Test
    void skipsOntoTarget() {
        final ValueList list = SlowValueListTest.list();
        list.skipTo(new DocId(1));
        Assertions.assertEquals("1=b", SlowValueListTest.at(list), "Target");
    }

    @Test
    void skipsPastEnd() {
        final ValueList list = SlowValueListTest.list();
        list.skipTo(new DocId(9));
        Assertions.assertTrue(list.atEnd(), "Past the last");
    }

    @Test
    void checksDocumentWithoutValue() {
        final ValueList list = SlowValueListTest.list();
        Assertions.assertEquals(
            "false 3=",
            String.format(
                "%b %s", list.check(new DocId(3)), SlowValueListTest.at(list)
            ),
            "Moved there, no value"
        );
    }

    @Test
    void checksBehindDocumentWithoutValue() {
        final ValueList list = SlowValueListTest.list();
        list.check(new DocId(3));
        Assertions.assertFalse(list.check(new DocId(2)), "Behind, no value");
    }

    @Test
    void checksDocumentWithValue() {
        final ValueList list = SlowValueListTest.list();
        Assertions.assertEquals(
            "true 4=d",
            String.format(
                "%b %s", list.check(new DocId(4)), SlowValueListTest.at(list)
            ),
            "Moved there, value read"
        );
    }

    @Test
    void checksBehindDocumentWithValue() {
        final ValueList list = SlowValueListTest.list();
        list.check(new DocId(4));
        Assertions.assertEquals(
            List.of(true, true),
            List.of(list.check(new DocId(4)), list.check(new DocId(1))),
            "Current value kept"
        );
    }

    @Test
    void checksDeletedDocument() {
        Assertions.assertFalse(
            SlowValueListTest.list().check(new DocId(2)), "Deleted"
        );
    }

    @Test
    void checksPastEnd() {
        final ValueList list = SlowValueListTest.list();
        Assertions.assertEquals(
            "true end",
            String.format(
                "%b %s", list.check(new DocId(5)), SlowValueListTest.at(list)
            ),
            "Past the last"
        );
    }

    @Test
    void skipsMissingDocument() {
        final ValueList list = new SlowValueList(
            SlowValueListTest.missing(), SlowValueListTest.SLOT
        );
        list.next();
        Assertions.assertEquals("4=d", SlowValueListTest.at(list), "Skipped");
    }

    @Test
    void checksMissingDocument() {
        Assertions.assertFalse(
            new SlowValueList(
                SlowValueListTest.missing(), SlowValueListTest.SLOT
            ).check(new DocId(1)),
            "Missing checked"
        );
    }

    @Test
    void describesPosition() {
        final ValueList list = SlowValueListTest.list();
        list.next();
        Assertions.assertEquals(
            "SlowValueList(slot=1, docid=1, value=\"b\")",
            list.description(),
            "On a document"
        );
    }

    @Test
    void describesEnd() {
        final ValueList list = SlowValueListTest.list();
        list.skipTo(new DocId(5));
        Assertions.assertEquals(
            "SlowValueList(slot=1, atend)", list.description(), "At end"
        );
    }

    private static String at(final ValueList list) {
        final String result;
        if (list.atEnd()) {
            result = "end";
        } else {
            result = String.format("%s=%s", list.docId(), list.value());
        }
        return result;
    }

    private static ValueList list() {
        return new SlowValueList(
            SlowValueListTest.filled(), SlowValueListTest.SLOT
        );
    }

    private static Shard missing() {
        final InMemoryShard origin = SlowValueListTest.filled();
        return Wrapped.of(
            origin,
            Map.of(
                "openDocument",
                args -> {
                    if (new DocId(1).equals(args[0])) {
                        throw new DocNotFoundError("gone");
                    }
                    return origin.openDocument((DocId) args[0], true);
                }
            )
        );
    }

    private static InMemoryShard filled() {
        final InMemoryShard shard = new InMemoryShard();
        shard.addDocument(SlowValueListTest.doc("b"));
        shard.addDocument(SlowValueListTest.doc("gone"));
        shard.addDocument(SlowValueListTest.doc(""));
        shard.addDocument(SlowValueListTest.doc("d"));
        shard.deleteDocument(new DocId(2));
        return shard;
    }

    private static DocumentInternal doc(final String value) {
        final DocumentInternal doc = new DocumentInternal();
        doc.setData(value);
        if (!value.isEmpty()) {
            doc.addValue(SlowValueListTest.SLOT, value);
        }
        doc.addValue(new ValueNo(2L), "other");
        return doc;
    }
}
