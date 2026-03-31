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

/**
 * Position of a leaf item within its tag and how its bytes are stored.
 *
 * @param number Component number from 1 to 65535, 1 for the first
 * @param compressed Whether the tag bytes are compressed
 * @param last Whether this is the last component of the tag
 * @since 0.1.0
 */
public record TagPart(int number, boolean compressed, boolean last) {

    /**
     * The sole, uncompressed component of a tag.
     */
    public static final TagPart WHOLE = new TagPart(1, false, true);

    /**
     * Creates a part.
     *
     * @param number Component number from 1 to 65535
     * @param compressed Whether the tag bytes are compressed
     * @param last Whether this is the last component of the tag
     */
    public TagPart {
        if (number < 1 || number > 0xFFFF) {
            throw new IllegalArgumentException(
                String.format("Bad component number: %d", number)
            );
        }
    }

    /**
     * Tests whether this is the first component, stored without a count.
     *
     * @return True for component 1
     */
    public boolean first() {
        return this.number == 1;
    }

    /**
     * Flags of the high size byte.
     *
     * @return Compressed, last and first bits
     */
    public int flags() {
        int flags = 0;
        if (this.compressed) {
            flags |= LeafItem.COMPRESSED_BIT;
        }
        if (this.last) {
            flags |= LeafItem.LAST_BIT;
        }
        if (this.first()) {
            flags |= LeafItem.FIRST_BIT;
        }
        return flags;
    }
}
