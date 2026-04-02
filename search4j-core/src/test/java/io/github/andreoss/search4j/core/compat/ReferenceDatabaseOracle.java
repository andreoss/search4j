/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public database checks using the held reference fixtures.
 */
package io.github.andreoss.search4j.core.compat;

import io.github.andreoss.search4j.core.api.Database;
import io.github.andreoss.search4j.core.api.DatabaseFactory;
import io.github.andreoss.search4j.core.api.PositionIterator;
import io.github.andreoss.search4j.core.api.TermIterator;
import io.github.andreoss.search4j.core.api.WritableDatabase;
import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.backend.vidrio.BlockSize;
import io.github.andreoss.search4j.core.backend.vidrio.TableType;
import io.github.andreoss.search4j.core.backend.vidrio.VidrioFormat;
import io.github.andreoss.search4j.core.types.DatabaseAction;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;

/**
 * Reference-derived checks of database creation and reads.
 *
 * @since 0.1.0
 */
final class ReferenceDatabaseOracle {

    /**
     * Utility class, no instances.
     */
    private ReferenceDatabaseOracle() {
    }

    static void build(final Path dir, final int docs, final String data) {
        try (
            WritableDatabase db = DatabaseFactory.openWritable(
                dir, DatabaseAction.CREATE.code(), BlockSize.MIN_BYTES
            )
        ) {
            for (int index = 0; index < docs; ++index) {
                final DocumentInternal doc = new DocumentInternal();
                doc.setData(data);
                db.addDocument(doc);
            }
            db.commit();
        }
    }

    static TermCount read(final Path dir, final int docs, final String data)
        throws IOException {
        Files.write(
            dir.resolve(VidrioFormat.versionFileName()),
            ReferenceFixtures.bytes("oversized/version")
        );
        for (final TableType type : List.of(
            TableType.POSTLIST, TableType.DOCDATA, TableType.TERMLIST
        )) {
            Files.write(
                dir.resolve(type.fileName()),
                ReferenceFixtures.bytes(
                    String.format("oversized/%s", type.tableName())
                )
            );
        }
        final TermCount result;
        try (Database db = DatabaseFactory.open(dir)) {
            result = db.docCount();
            Assertions.assertEquals(
                List.of(
                    result, DocId.of(docs), data,
                    TermCount.ZERO, false, false
                ),
                List.of(
                    db.docCount(), db.lastDocId(),
                    db.document(DocId.of(1)).data(),
                    db.docLength(DocId.of(50)), db.hasPositions(),
                    db.allTerms("").hasNext()
                ),
                "Reference database read through the public API"
            );
        }
        Surfaces.covered("vidrio.database.read");
        return result;
    }

    static List<Object> auxiliary(final Path dir) throws IOException {
        final Path location = dir.resolve("auxiliary");
        Files.createDirectory(location);
        try (
            WritableDatabase db = DatabaseFactory.openWritable(
                location, DatabaseAction.CREATE.code(), BlockSize.MIN_BYTES
            )
        ) {
            final DocumentInternal doc = new DocumentInternal();
            doc.addPosting("alpha", new TermPos(2), new TermCount(1));
            doc.addPosting("alpha", new TermPos(5), new TermCount(1));
            doc.addValue(new ValueNo(7), "value");
            db.addDocument(doc);
            db.setMetadata("key", "value");
            db.commit();
        }
        final List<Object> result = new ArrayList<>(5);
        try (Database db = DatabaseFactory.open(location)) {
            result.add(db.positionCount(DocId.of(1), "alpha"));
            try (
                PositionIterator positions = db.positions(
                    DocId.of(1),
                    "alpha"
                )
            ) {
                final List<TermPos> found = new ArrayList<>(2);
                while (positions.hasNext()) {
                    found.add(positions.next());
                }
                result.add(found);
            }
            result.add(db.document(DocId.of(1)).value(new ValueNo(7)));
            result.add(db.metadata("key"));
            try (TermIterator keys = db.metadataKeys("")) {
                result.add(keys.next());
            }
        }
        return result;
    }
}
