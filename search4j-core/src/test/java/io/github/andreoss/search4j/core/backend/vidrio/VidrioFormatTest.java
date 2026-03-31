/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link VidrioFormat}, {@link TableType} and the item layout.
 *
 * @since 0.1.0
 */
final class VidrioFormatTest {

    @Test
    void definesFormatLimits() {
        Assertions.assertEquals(
            List.of(10, -1L),
            List.of(VidrioFormat.CURSOR_LEVELS, VidrioFormat.MAX_DOCID),
            "Cursor levels and largest document number"
        );
    }

    @Test
    void definesExtensionBytes() {
        Assertions.assertArrayEquals(
            new byte[] {0x67, 0x6C, 0x61, 0x73, 0x73},
            VidrioFormat.extension(),
            "Extension bytes"
        );
    }

    @Test
    void copiesExtensionBytes() {
        VidrioFormat.extension()[0] = 0;
        Assertions.assertEquals(
            0x67, VidrioFormat.extension()[0], "Unchanged after write"
        );
    }

    @Test
    void spellsExtensionText() {
        Assertions.assertArrayEquals(
            VidrioFormat.extension(),
            VidrioFormat.extensionText().getBytes(StandardCharsets.US_ASCII),
            "Text matches bytes"
        );
    }

    @Test
    void namesTableFiles() {
        Assertions.assertEquals(
            String.format("docdata.%s", VidrioFormat.extensionText()),
            TableType.DOCDATA.fileName(),
            "Document data file"
        );
    }

    @Test
    void listsTablesInOrder() {
        Assertions.assertEquals(
            "postlist docdata termlist position spelling synonym",
            Stream.of(TableType.values()).map(TableType::tableName)
                .collect(Collectors.joining(" ")),
            "Table order"
        );
    }

    @Test
    void marksLazyTables() {
        Assertions.assertEquals(
            List.of(
                TableLaziness.EAGER, TableLaziness.LAZY, TableLaziness.CHOSEN,
                TableLaziness.LAZY, TableLaziness.LAZY, TableLaziness.LAZY
            ),
            Stream.of(TableType.values()).map(TableType::laziness).toList(),
            "Laziness per table"
        );
    }

    @Test
    void definesFieldWidths() {
        Assertions.assertEquals(
            List.of(1, 2, 2, 2, 4, 11),
            List.of(
                ItemKey.LENGTH_BYTES, LeafItem.SIZE_BYTES,
                BlockHeader.ENTRY_BYTES, TableItem.COUNT_BYTES,
                BlockNumber.BYTES, BlockHeader.BYTES
            ),
            "K1 I2 D2 X2 block number and directory start"
        );
    }

    @Test
    void definesItemFlags() {
        Assertions.assertEquals(
            List.of(0x80, 0x40, 0x20, 0xE0, 0x1FFF, 8194),
            List.of(
                LeafItem.COMPRESSED_BIT, LeafItem.LAST_BIT,
                LeafItem.FIRST_BIT, LeafItem.FLAG_MASK,
                LeafItem.SIZE_MASK, LeafItem.MAX_SIZE
            ),
            "Flags and size masks"
        );
    }

    @Test
    void derivesSizeMaskFromFlags() {
        Assertions.assertEquals(
            0xFFFF & ~(LeafItem.FLAG_MASK << 8),
            LeafItem.SIZE_MASK,
            "Size mask excludes flags"
        );
    }

    @Test
    void definesVersionFileBytes() {
        Assertions.assertEquals(
            List.of(
                14, "0f0d58617069616e20476c617373", 0x046E,
                "iam".concat(VidrioFormat.extensionText()), 20_160_314,
                20_151_224
            ),
            List.of(
                VidrioFormat.MAGIC_BYTES,
                HexFormat.of().formatHex(VidrioFormat.versionMagic()),
                VidrioFormat.FORMAT_VERSION, VidrioFormat.versionFileName(),
                VidrioFormat.versionDate(VidrioFormat.FORMAT_VERSION),
                VidrioFormat.versionDate(VidrioFormat.dateVersion(2015, 12, 24))
            ),
            "Magic, format version, version file name and date mapping"
        );
    }

    @Test
    void copiesVersionMagic() {
        final byte[] magic = VidrioFormat.versionMagic();
        magic[0] = 0;
        Assertions.assertEquals(
            0x0F, VidrioFormat.versionMagic()[0], "Fresh copy each time"
        );
    }

    @Test
    void definesCompressionPerTable() {
        Assertions.assertEquals(
            List.of(0, 18, 18, 0, 18, 18),
            Stream.of(TableType.values()).map(TableType::compression)
                .collect(Collectors.toList()),
            "Posting and position tables never compressed"
        );
    }

    @Test
    void definesBlockLimits() {
        Assertions.assertEquals(
            List.of(255, 4, 254),
            List.of(
                ItemKey.MAX_LENGTH, LeafItem.BLOCK_CAPACITY,
                BlockHeader.LEVEL_FREELIST
            ),
            "Key length, capacity and free-list level"
        );
    }
}
