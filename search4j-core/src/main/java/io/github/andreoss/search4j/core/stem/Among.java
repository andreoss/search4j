/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Snowball stemming runtime and language registry.
 */
package io.github.andreoss.search4j.core.stem;

/**
 * One entry of a Snowball among table.
 *
 * <p>A table entry matches a word slice against its text. The substring
 * index is the length of the longest common prefix or suffix the reference
 * tables share with the previous entry; it is an optimisation hint and does
 * not change which entry matches.</p>
 *
 * @param text Text matched against the current buffer
 * @param substring Shared prefix or suffix length with the previous entry
 * @param result Value returned when the entry matches
 * @since 0.1.0
 */
public record Among(String text, int substring, int result) {
}
