/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the constant enumerations.
 */
package io.github.andreoss.search4j.core.types;

import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests that the constant enumerations carry the expected numeric values.
 *
 * @since 0.1.0
 */
final class ConstantsTest {

    @Test
    void createOrOpenCode() {
        Assertions.assertEquals(
            0x00, DatabaseAction.CREATE_OR_OPEN.code(), "Database value"
        );
    }

    @Test
    void createOrOverwriteCode() {
        Assertions.assertEquals(
            0x01, DatabaseAction.CREATE_OR_OVERWRITE.code(), "Database value"
        );
    }

    @Test
    void createCode() {
        Assertions.assertEquals(
            0x02, DatabaseAction.CREATE.code(), "Database value"
        );
    }

    @Test
    void openCode() {
        Assertions.assertEquals(
            0x03, DatabaseAction.OPEN.code(), "Database value"
        );
    }

    @Test
    void actionMask() {
        Assertions.assertEquals(0x03, DatabaseAction.MASK, "Two-bit mask");
    }

    @Test
    void resolvesOpenFromFlags() {
        Assertions.assertEquals(
            Optional.of(DatabaseAction.OPEN),
            DatabaseAction.of(DbFlag.NO_SYNC.add(DatabaseAction.OPEN.code())),
            "Action read from the low two bits"
        );
    }

    @Test
    void resolvesCreateOrOpenFromEmptyFlags() {
        Assertions.assertEquals(
            Optional.of(DatabaseAction.CREATE_OR_OPEN),
            DatabaseAction.of(0),
            "Absent action means create or open"
        );
    }

    @Test
    void actionSurvivesCombinedFlags() {
        Assertions.assertEquals(
            Optional.of(DatabaseAction.OPEN),
            DatabaseAction.of(
                DbFlag.NO_TERMLIST.add(
                    Backend.IN_MEMORY.add(DatabaseAction.OPEN.code())
                )
            ),
            "Action survives combined flags"
        );
    }

    @Test
    void vidrioCode() {
        Assertions.assertEquals(0x100, Backend.VIDRIO.code(), "Vidrio value");
    }

    @Test
    void stubCode() {
        Assertions.assertEquals(0x300, Backend.STUB.code(), "Stub value");
    }

    @Test
    void inMemoryCode() {
        Assertions.assertEquals(0x400, Backend.IN_MEMORY.code(), "Memory value");
    }

    @Test
    void backendMask() {
        Assertions.assertEquals(0x700, Backend.MASK, "Three-bit mask");
    }

    @Test
    void resolvesVidrioFromFlags() {
        Assertions.assertEquals(
            Optional.of(Backend.VIDRIO),
            Backend.of(DbFlag.NO_SYNC.add(Backend.VIDRIO.code())),
            "Backend read from bits 8 to 10"
        );
    }

    @Test
    void resolvesEmptyBackend() {
        Assertions.assertEquals(Optional.empty(), Backend.of(0), "No backend");
    }

    @Test
    void selectsNamedBackend() {
        Assertions.assertTrue(
            Backend.VIDRIO.selected(Backend.VIDRIO.code()),
            "Selected backend recognised"
        );
    }

    @Test
    void rejectsOtherBackend() {
        Assertions.assertFalse(
            Backend.STUB.selected(Backend.VIDRIO.code()),
            "Different backend not selected"
        );
    }

    @Test
    void noSyncCode() {
        Assertions.assertEquals(0x04, DbFlag.NO_SYNC.code(), "No-sync value");
    }

    @Test
    void fullSyncCode() {
        Assertions.assertEquals(0x08, DbFlag.FULL_SYNC.code(), "Full-sync value");
    }

    @Test
    void dangerousCode() {
        Assertions.assertEquals(0x10, DbFlag.DANGEROUS.code(), "Dangerous value");
    }

    @Test
    void noTermlistCode() {
        Assertions.assertEquals(0x20, DbFlag.NO_TERMLIST.code(), "No-termlist value");
    }

    @Test
    void retryLockCode() {
        Assertions.assertEquals(0x40, DbFlag.RETRY_LOCK.code(), "Retry-lock value");
    }

    @Test
    void addedDbFlagPresent() {
        Assertions.assertTrue(
            DbFlag.NO_SYNC.present(DbFlag.NO_SYNC.add(DbFlag.DANGEROUS.add(0))),
            "Flag present"
        );
    }

    @Test
    void absentDbFlagNotPresent() {
        Assertions.assertFalse(
            DbFlag.RETRY_LOCK.present(
                DbFlag.NO_SYNC.add(DbFlag.DANGEROUS.add(0))
            ),
            "Absent flag not present"
        );
    }

    @Test
    void shortTreeCode() {
        Assertions.assertEquals(1, CheckFlag.SHORT_TREE.code(), "Short-tree value");
    }

    @Test
    void fullTreeCode() {
        Assertions.assertEquals(2, CheckFlag.FULL_TREE.code(), "Full-tree value");
    }

    @Test
    void showFreelistCode() {
        Assertions.assertEquals(4, CheckFlag.SHOW_FREELIST.code(), "Freelist value");
    }

    @Test
    void showStatsCode() {
        Assertions.assertEquals(8, CheckFlag.SHOW_STATS.code(), "Stats value");
    }

    @Test
    void fixCode() {
        Assertions.assertEquals(16, CheckFlag.FIX.code(), "Fix value");
    }

    @Test
    void checkFlagsCombine() {
        Assertions.assertTrue(
            CheckFlag.FIX.present(CheckFlag.FIX.add(CheckFlag.SHOW_STATS.add(0))),
            "Flags combine"
        );
    }

    @Test
    void absentCheckFlagNotPresent() {
        Assertions.assertFalse(
            CheckFlag.FULL_TREE.present(CheckFlag.FIX.add(0)),
            "Absent flag not present"
        );
    }

    @Test
    void noRenumberCode() {
        Assertions.assertEquals(4, CompactFlag.NO_RENUMBER.code(), "Renumber value");
    }

    @Test
    void multipassCode() {
        Assertions.assertEquals(8, CompactFlag.MULTIPASS.code(), "Multipass value");
    }

    @Test
    void singleFileCode() {
        Assertions.assertEquals(16, CompactFlag.SINGLE_FILE.code(), "Single-file value");
    }

    @Test
    void compactFlagsCombine() {
        Assertions.assertTrue(
            CompactFlag.MULTIPASS.present(CompactFlag.MULTIPASS.add(0)),
            "Flags combine"
        );
    }

    @Test
    void absentCompactFlagNotPresent() {
        Assertions.assertFalse(
            CompactFlag.SINGLE_FILE.present(0), "Absent flag not present"
        );
    }

    @Test
    void andCode() {
        Assertions.assertEquals(0, QueryOp.AND.code(), "AND value");
    }

    @Test
    void orCode() {
        Assertions.assertEquals(1, QueryOp.OR.code(), "OR value");
    }

    @Test
    void andNotCode() {
        Assertions.assertEquals(2, QueryOp.AND_NOT.code(), "AND_NOT value");
    }

    @Test
    void xorCode() {
        Assertions.assertEquals(3, QueryOp.XOR.code(), "XOR value");
    }

    @Test
    void andMaybeCode() {
        Assertions.assertEquals(4, QueryOp.AND_MAYBE.code(), "AND_MAYBE value");
    }

    @Test
    void filterCode() {
        Assertions.assertEquals(5, QueryOp.FILTER.code(), "FILTER value");
    }

    @Test
    void nearCode() {
        Assertions.assertEquals(6, QueryOp.NEAR.code(), "NEAR value");
    }

    @Test
    void phraseCode() {
        Assertions.assertEquals(7, QueryOp.PHRASE.code(), "PHRASE value");
    }

    @Test
    void valueRangeCode() {
        Assertions.assertEquals(8, QueryOp.VALUE_RANGE.code(), "VALUE_RANGE value");
    }

    @Test
    void scaleWeightCode() {
        Assertions.assertEquals(9, QueryOp.SCALE_WEIGHT.code(), "SCALE_WEIGHT value");
    }

    @Test
    void eliteSetCode() {
        Assertions.assertEquals(10, QueryOp.ELITE_SET.code(), "ELITE_SET value");
    }

    @Test
    void valueGeCode() {
        Assertions.assertEquals(11, QueryOp.VALUE_GE.code(), "VALUE_GE value");
    }

    @Test
    void valueLeCode() {
        Assertions.assertEquals(12, QueryOp.VALUE_LE.code(), "VALUE_LE value");
    }

    @Test
    void synonymCode() {
        Assertions.assertEquals(13, QueryOp.SYNONYM.code(), "SYNONYM value");
    }

    @Test
    void maxCode() {
        Assertions.assertEquals(14, QueryOp.MAX.code(), "MAX value");
    }

    @Test
    void wildcardCode() {
        Assertions.assertEquals(15, QueryOp.WILDCARD.code(), "WILDCARD value");
    }

    @Test
    void invalidCode() {
        Assertions.assertEquals(99, QueryOp.INVALID.code(), "INVALID value");
    }

    @Test
    void leafTermCode() {
        Assertions.assertEquals(100, QueryOp.LEAF_TERM.code(), "Term leaf value");
    }

    @Test
    void leafPostingSourceCode() {
        Assertions.assertEquals(
            101, QueryOp.LEAF_POSTING_SOURCE.code(), "Posting-source leaf value"
        );
    }

    @Test
    void leafMatchAllCode() {
        Assertions.assertEquals(102, QueryOp.LEAF_MATCH_ALL.code(), "Match-all leaf value");
    }

    @Test
    void leafMatchNothingCode() {
        Assertions.assertEquals(
            103, QueryOp.LEAF_MATCH_NOTHING.code(), "Match-nothing leaf value"
        );
    }

    @Test
    void termIsLeaf() {
        Assertions.assertTrue(QueryOp.LEAF_TERM.leaf(), "Term is a leaf");
    }

    @Test
    void operatorIsNotLeaf() {
        Assertions.assertFalse(QueryOp.AND.leaf(), "Operator is not a leaf");
    }

    @Test
    void invalidIsNotLeaf() {
        Assertions.assertFalse(
            QueryOp.INVALID.leaf(), "Invalid placeholder is not a leaf"
        );
    }

    @Test
    void resolvesSynonymByCode() {
        Assertions.assertEquals(
            QueryOp.SYNONYM, QueryOp.of(13), "Code 13 is synonym"
        );
    }

    @Test
    void rejectsUnknownCode() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> QueryOp.of(16),
            "Unknown code rejected"
        );
    }

    @Test
    void stemStrategyCount() {
        Assertions.assertEquals(5, StemStrategy.values().length, "Five strategies");
    }

    @Test
    void stopStrategyCount() {
        Assertions.assertEquals(3, StopStrategy.values().length, "Three strategies");
    }

    @Test
    void wildcardLimitCount() {
        Assertions.assertEquals(3, WildcardLimit.values().length, "Three limits");
    }
}
