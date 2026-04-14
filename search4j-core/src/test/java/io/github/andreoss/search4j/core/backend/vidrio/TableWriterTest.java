/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseClosedError;
import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.UnimplementedError;
import io.github.andreoss.search4j.core.ser.Zlib;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link TableWriter}, read back with {@link VidrioTable} and
 * checked against a sorted map model.
 *
 * @since 0.1.0
 */
final class TableWriterTest {

    /**
     * Compression threshold used.
     */
    private static final int COMPRESS = 18;

    @Test
    void readsUncommittedLeafRootWithoutWritingIt() {
        final Map<Long, byte[]> data = new HashMap<>();
        final TableWriter table =
            TableWriter.create(new MemoryBlocks(data, BlockSize.MIN), 0);
        final boolean before = table.contains(Trees.key("a"));
        final int empty = data.size();
        table.add(Trees.key("a"), Trees.key("b"));
        final boolean after = table.get(Trees.key("a")).isPresent();
        final int unwritten = data.size();
        table.commit(new RevisionNumber(1L));
        Assertions.assertEquals(
            List.of(false, 0, true, 0, 1),
            List.of(before, empty, after, unwritten, data.size()),
            "A leaf root is read from memory and written on commit only"
        );
    }

    @Test
    void keepsRandomChangesAcrossRevisions() {
        final Workload work = new Workload(42L, BlockSize.MIN);
        final TableWriter table =
            TableWriter.create(work.blocks(), TableWriterTest.COMPRESS);
        final List<Object> faults = new ArrayList<>(0);
        TableState state = null;
        for (int rev = 1; rev <= 4; ++rev) {
            work.apply(table, 1000, 20);
            state = table.commit(new RevisionNumber(rev));
            if (!work.expected().equals(work.read(state))) {
                faults.add(rev);
            }
            faults.addAll(new Audit(work.blocks(), state).faults());
        }
        Assertions.assertEquals(
            List.of(List.of(), true, state.entries().value()),
            List.of(
                faults, state.root().level() >= 2,
                (long) work.model().size()
            ),
            "Every revision matches the model, every block accounted"
        );
    }

    @Test
    void keepsChangesOfLargeBlocks() {
        final List<Object> faults = new ArrayList<>(0);
        for (final BlockSize size : List.of(BlockSize.DEFAULT, BlockSize.MAX)) {
            final Workload work = new Workload(5L, size);
            final TableWriter table = TableWriter.create(work.blocks(), 0);
            work.apply(table, 600, 30);
            final byte[] big = new byte[70_000];
            new Random(3L).nextBytes(big);
            work.model().put(Trees.key("big"), big);
            table.add(Trees.key("big"), big);
            final TableState state = table.commit(new RevisionNumber(1L));
            if (!work.expected().equals(work.read(state))) {
                faults.add(size);
            }
            faults.addAll(new Audit(work.blocks(), state).faults());
        }
        Assertions.assertEquals(List.of(), faults, "Large blocks read back");
    }

    @Test
    void deletesEveryEntryAndShrinksRoot() {
        final Workload work = new Workload(9L, BlockSize.MIN);
        final TableWriter table = TableWriter.create(work.blocks(), 0);
        work.apply(table, 2000, 0);
        final TableState full = table.commit(new RevisionNumber(1L));
        for (final byte[] key : work.model().keySet()) {
            table.delete(key);
        }
        work.model().clear();
        final TableState empty = table.commit(new RevisionNumber(2L));
        Assertions.assertEquals(
            List.of(true, 0, 0L, List.of(), List.of()),
            List.of(
                full.root().level() > 0, empty.root().level(),
                empty.entries().value(), work.read(empty),
                new Audit(work.blocks(), empty).faults()
            ),
            "Empty again, one level, blocks freed"
        );
    }

    @Test
    void fillsBlocksOfAdditionsInKeyOrder() {
        final List<byte[]> keys = TableWriterTest.keys(3000);
        final TableState sorted = TableWriterTest.fill(keys, false);
        Collections.shuffle(keys, new Random(1L));
        final TableState shuffled = TableWriterTest.fill(keys, false);
        Assertions.assertEquals(
            List.of(true, false, 52L, true),
            List.of(
                sorted.sequential(), shuffled.sequential(),
                sorted.free().unused().value(),
                shuffled.free().unused().value() > 65L
            ),
            "Sequential additions fill 51 leaves of 59 items and the root"
        );
    }

    @Test
    void fillsBlocksInFullCompaction() {
        final List<byte[]> keys = TableWriterTest.keys(3000);
        final TableState plain = TableWriterTest.fill(keys, false);
        final TableState full = TableWriterTest.fill(keys, true);
        Assertions.assertEquals(
            List.of(true, plain.entries()),
            List.of(
                full.free().unused().value() <= plain.free().unused().value(),
                full.entries()
            ),
            "Full compaction uses no more blocks"
        );
    }

    @Test
    void splitsLongTagsInFullCompaction() {
        final MemoryBlocks blocks = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        final TableWriter table = TableWriter.create(blocks, 0);
        table.compaction(true);
        final List<byte[]> tags = new ArrayList<>(60);
        final Random random = new Random(8L);
        for (int idx = 0; idx < 60; ++idx) {
            final byte[] tag = new byte[300 + random.nextInt(900)];
            random.nextBytes(tag);
            tags.add(tag);
            table.add(TableWriterTest.numbered(idx), tag);
        }
        final TableState state = table.commit(new RevisionNumber(1L));
        final List<Boolean> same = new ArrayList<>(60);
        final VidrioTable read = VidrioTable.over(blocks, state.root());
        for (int idx = 0; idx < 60; ++idx) {
            same.add(
                Arrays.equals(
                    tags.get(idx),
                    read.get(TableWriterTest.numbered(idx)).orElseThrow()
                )
            );
        }
        Assertions.assertEquals(
            Collections.nCopies(60, true), same, "Long tags read back"
        );
    }

    @Test
    void dropsChangesOnCancel() {
        final Workload work = new Workload(11L, BlockSize.MIN);
        final TableWriter table = TableWriter.create(work.blocks(), 0);
        work.apply(table, 3000, 0);
        final TableState first = table.commit(new RevisionNumber(1L));
        final List<String> kept = work.expected();
        final List<byte[]> keys = new ArrayList<>(work.model().keySet());
        for (final byte[] key : keys) {
            table.delete(key);
        }
        table.cancel(first);
        final boolean clean = table.modified();
        final List<String> after = work.read(first);
        for (final byte[] key : keys.subList(0, keys.size() / 2)) {
            work.model().remove(key);
            table.delete(key);
        }
        work.apply(table, 500, 10);
        final TableState second = table.commit(new RevisionNumber(2L));
        Assertions.assertEquals(
            List.of(false, kept, work.expected(), List.of(), work.model().size()),
            List.of(
                clean, after, work.read(second),
                new Audit(work.blocks(), second).faults(),
                (int) second.entries().value()
            ),
            "Cancelled changes dropped, later changes kept"
        );
    }

    @Test
    void cancelsFromNewTable() {
        final Workload work = new Workload(12L, BlockSize.MIN);
        final TableWriter table = TableWriter.create(work.blocks(), 0);
        final TableState none = TableState.initial(BlockSize.MIN, 0);
        new Workload(13L, BlockSize.MIN).apply(table, 300, 0);
        table.cancel(none);
        work.apply(table, 300, 0);
        final TableState state = table.commit(new RevisionNumber(1L));
        Assertions.assertEquals(
            List.of(work.expected(), List.of()),
            List.of(work.read(state), new Audit(work.blocks(), state).faults()),
            "Only the changes after the cancel"
        );
    }

    @Test
    void replacesTagsOfEveryLength() {
        final Workload work = new Workload(14L, BlockSize.MIN);
        final TableWriter table = TableWriter.create(work.blocks(), 0);
        work.apply(table, 400, 0);
        final List<byte[]> keys = new ArrayList<>(work.model().keySet());
        final Random random = new Random(15L);
        for (int round = 0; round < 3; ++round) {
            for (final byte[] key : keys) {
                final byte[] tag = new byte[random.nextInt(3) * random.nextInt(1500)];
                random.nextBytes(tag);
                work.model().put(key, tag);
                table.add(key, tag);
            }
        }
        final TableState state = table.commit(new RevisionNumber(1L));
        Assertions.assertEquals(
            List.of(work.expected(), (long) keys.size(), List.of()),
            List.of(
                work.read(state), state.entries().value(),
                new Audit(work.blocks(), state).faults()
            ),
            "Replaced tags read back, count unchanged"
        );
    }

    @Test
    void commitsManySmallRevisions() {
        final Workload work = new Workload(16L, BlockSize.MIN);
        final TableWriter table = TableWriter.create(work.blocks(), 0);
        work.apply(table, 50, 0);
        TableState state = table.commit(new RevisionNumber(1L));
        for (int rev = 2; rev <= 1000; ++rev) {
            work.apply(table, 1, 0);
            state = table.commit(new RevisionNumber(rev));
        }
        Assertions.assertEquals(
            List.of(work.expected(), List.of()),
            List.of(work.read(state), new Audit(work.blocks(), state).faults()),
            "Free-list blocks started and reused over many revisions"
        );
    }

    @Test
    void continuesFromCommittedState() {
        final Workload work = new Workload(17L, BlockSize.MIN);
        final TableWriter first = TableWriter.create(work.blocks(), 0);
        work.apply(first, 800, 10);
        final TableWriter again = TableWriter.open(
            work.blocks(), first.commit(new RevisionNumber(3L))
        );
        work.apply(again, 800, 30);
        final TableState next = again.commit(new RevisionNumber(4L));
        Assertions.assertEquals(
            List.of(work.expected(), List.of()),
            List.of(work.read(next), new Audit(work.blocks(), next).faults()),
            "Reopened table continues"
        );
    }

    @Test
    void compressesLongCompressibleTag() {
        final MemoryBlocks blocks = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        final TableWriter table =
            TableWriter.create(blocks, TableWriterTest.COMPRESS);
        final byte[] tag = new byte[5000];
        Arrays.fill(tag, (byte) 'a');
        table.add(Trees.key("k"), tag);
        table.add(Trees.key("s"), Trees.key("aaaaaaaaaaaaaaaaaa"));
        final TableState state = table.commit(new RevisionNumber(1L));
        final Block leaf = blocks.read(state.root().block());
        Assertions.assertEquals(
            List.of(true, false, true),
            List.of(
                leaf.compressed(1), leaf.compressed(2),
                Arrays.equals(
                    tag,
                    VidrioTable.over(blocks, state.root())
                        .get(Trees.key("k")).orElseThrow()
                )
            ),
            "Long tag compressed, tag at the threshold not"
        );
    }

    @Test
    void keepsIncompressibleTagPlain() {
        final MemoryBlocks blocks = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        final TableWriter table =
            TableWriter.create(blocks, TableWriterTest.COMPRESS);
        final byte[] tag = new byte[100];
        new Random(2L).nextBytes(tag);
        table.add(Trees.key("k"), tag);
        final TableState state = table.commit(new RevisionNumber(1L));
        Assertions.assertEquals(
            List.of(false, true),
            List.of(
                blocks.read(state.root().block()).compressed(1),
                Arrays.equals(
                    tag,
                    VidrioTable.over(blocks, state.root())
                        .get(Trees.key("k")).orElseThrow()
                )
            ),
            "Incompressible tag stored as is"
        );
    }

    @Test
    void storesPrecompressedTag() {
        final MemoryBlocks blocks = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        final TableWriter table = TableWriter.create(blocks, 0);
        final byte[] tag = Trees.key("precompressed tag");
        table.addCompressed(Trees.key("k"), Zlib.compress(tag));
        Assertions.assertArrayEquals(
            tag,
            VidrioTable.over(
                blocks, table.commit(new RevisionNumber(1L)).root()
            ).get(Trees.key("k")).orElseThrow(),
            "Tag inflated on read"
        );
    }

    @Test
    void replacesRootItemOfEmptyKey() {
        final MemoryBlocks blocks = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        final TableWriter table = TableWriter.create(blocks, 0);
        table.add(new byte[0], Trees.key("root"));
        final TableState state = table.commit(new RevisionNumber(1L));
        final TableCursor cursor =
            VidrioTable.over(blocks, state.root()).cursor();
        cursor.rewind();
        Assertions.assertEquals(
            List.of(0L, "root", false),
            List.of(
                state.entries().value(), new String(cursor.tag(), StandardCharsets.US_ASCII),
                table.delete(new byte[0])
            ),
            "Root item replaced, never deleted"
        );
    }

    @Test
    void tracksModification() {
        final TableWriter table = TableWriter.create(
            new MemoryBlocks(new HashMap<>(), BlockSize.MIN), 0
        );
        final boolean fresh = table.modified();
        table.add(Trees.key("a"), new byte[1]);
        final boolean added = table.modified();
        table.commit(new RevisionNumber(1L));
        final boolean committed = table.modified();
        final boolean missing = table.delete(Trees.key("b"));
        final boolean untouched = table.modified();
        table.delete(Trees.key("a"));
        Assertions.assertEquals(
            List.of(false, true, false, false, false, true, 0L),
            List.of(
                fresh, added, committed, missing, untouched, table.modified(),
                table.entries().value()
            ),
            "Modified after a change until the commit"
        );
    }

    @Test
    void rejectsLongKey() {
        final TableWriter table = TableWriter.create(
            new MemoryBlocks(new HashMap<>(), BlockSize.MIN), 0
        );
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> table.add(new byte[256], new byte[1]),
            "Key over 255 bytes"
        );
    }

    @Test
    void ignoresDeletionOfKeysNeverStored() {
        final TableWriter table = TableWriter.create(
            new MemoryBlocks(new HashMap<>(), BlockSize.MIN), 0
        );
        table.add(new byte[255], new byte[1]);
        Assertions.assertEquals(
            List.of(false, false, true),
            List.of(
                table.delete(new byte[256]), table.delete(new byte[0]),
                table.delete(new byte[255])
            ),
            "Only a stored key is deleted"
        );
    }

    @Test
    void rejectsLowRevision() {
        final TableWriter table = TableWriter.create(
            new MemoryBlocks(new HashMap<>(), BlockSize.MIN), 0
        );
        table.commit(new RevisionNumber(2L));
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> table.commit(new RevisionNumber(2L)),
            "Revision not above the one open"
        );
    }

    @Test
    void rejectsUseAfterClose() {
        final MemoryBlocks blocks = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        final TableWriter table = TableWriter.create(blocks, 0);
        table.close();
        final List<Class<?>> errors = new ArrayList<>(4);
        for (final Runnable call : List.<Runnable>of(
            () -> table.add(Trees.key("a"), new byte[0]),
            () -> table.delete(Trees.key("a")),
            () -> table.commit(new RevisionNumber(1L)),
            () -> table.cancel(TableState.initial(BlockSize.MIN, 0))
        )) {
            errors.add(
                Assertions.assertThrows(RuntimeException.class, call::run)
                    .getClass()
            );
        }
        Assertions.assertEquals(
            List.of(
                Collections.nCopies(4, DatabaseClosedError.class), 1
            ),
            List.of(errors, blocks.closes()),
            "Closed table rejects every change"
        );
    }

    @Test
    void rejectsTagOfTooManyComponents() {
        final TableWriter table = TableWriter.create(
            new MemoryBlocks(new HashMap<>(), BlockSize.MIN), 0
        );
        Assertions.assertThrows(
            UnimplementedError.class,
            () -> table.add(Trees.key("k"), new byte[502 * 0xFFFF]),
            "Over 65535 components"
        );
    }

    @Test
    void storesTagOfMostComponents() {
        final MemoryBlocks blocks = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        final TableWriter table = TableWriter.create(blocks, 0);
        final byte[] tag = new byte[499 + 501 * 0xFFFE];
        tag[tag.length - 1] = 1;
        table.add(Trees.key("k"), tag);
        Assertions.assertArrayEquals(
            tag,
            VidrioTable.over(
                blocks, table.commit(new RevisionNumber(1L)).root()
            ).get(Trees.key("k")).orElseThrow(),
            "65535 components"
        );
    }

    @Test
    void rejectsCancelToOtherBlockSize() {
        final TableWriter table = TableWriter.create(
            new MemoryBlocks(new HashMap<>(), BlockSize.MIN), 0
        );
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> table.cancel(TableState.initial(BlockSize.DEFAULT, 0)),
            "Cancel to another block size"
        );
    }

    @Test
    void closesStoreOfOtherBlockSize() {
        final MemoryBlocks blocks = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> TableWriter.open(
                blocks, TableState.initial(BlockSize.DEFAULT, 0)
            ),
            "Open of another block size"
        );
        Assertions.assertEquals(1, blocks.closes(), "Store closed");
    }

    @Test
    void closesStoreWhenRootUnreadable() {
        final MemoryBlocks blocks = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> TableWriter.open(
                blocks,
                new TableState(
                    new TableRoot(
                        new BlockNumber(5L), 0, BlockSize.MIN,
                        new RevisionNumber(1L), false
                    ),
                    EntryCount.EMPTY, true, 0, FreeListInfo.EMPTY
                )
            ),
            "No root block"
        );
        Assertions.assertEquals(1, blocks.closes(), "Store closed");
    }

    @Test
    void rejectsRootNewerThanRevision() {
        final Workload work = new Workload(18L, BlockSize.MIN);
        final TableWriter table = TableWriter.create(work.blocks(), 0);
        work.apply(table, 10, 0);
        final TableState state = table.commit(new RevisionNumber(4L));
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> TableWriter.open(
                work.blocks(),
                new TableState(
                    new TableRoot(
                        state.root().block(), 0, BlockSize.MIN,
                        new RevisionNumber(0L), false
                    ),
                    state.entries(), true, 0, state.free()
                )
            ),
            "Root written after the revision"
        );
    }

    @Test
    void rejectsChildNewerThanParent() {
        final Workload work = new Workload(19L, BlockSize.MIN);
        final TableWriter table = TableWriter.create(work.blocks(), 0);
        work.apply(table, 800, 0);
        final TableState state = table.commit(new RevisionNumber(1L));
        final Block top = work.blocks().read(state.root().block());
        work.blocks().bytes(top.child(1).value())[3] = 9;
        final TableWriter again = TableWriter.open(work.blocks(), state);
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> again.add(top.branchKey(1).data(), new byte[1]),
            "Child newer than its parent"
        );
    }

    @Test
    void rejectsChildOfWrongLevel() {
        final Workload work = new Workload(20L, BlockSize.MIN);
        final TableWriter table = TableWriter.create(work.blocks(), 0);
        work.apply(table, 800, 0);
        final TableState state = table.commit(new RevisionNumber(1L));
        final Block top = work.blocks().read(state.root().block());
        work.blocks().bytes(top.child(1).value())[4] = 7;
        final TableWriter again = TableWriter.open(work.blocks(), state);
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> again.delete(top.branchKey(1).data()),
            "Child at the wrong level"
        );
    }

    @Test
    void closesOnFailedCommit() {
        final MemoryBlocks blocks = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        final TableWriter table = TableWriter.create(blocks, 0);
        table.add(Trees.key("a"), new byte[1]);
        blocks.broken(true);
        final String failure = TableWriterTest.failure(table);
        blocks.broken(false);
        Assertions.assertEquals(
            List.of("Error writing block", 1, DatabaseClosedError.class),
            List.of(
                failure, blocks.closes(), TableWriterTest.addition(table)
            ),
            "Closed after a failed commit"
        );
    }

    @Test
    void keepsOrderFlagAcrossReopen() {
        final List<byte[]> keys = TableWriterTest.keys(50);
        Collections.shuffle(keys, new Random(2L));
        final MemoryBlocks blocks = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        final TableWriter first = TableWriter.create(blocks, 0);
        for (final byte[] key : keys) {
            first.add(key, new byte[4]);
        }
        final TableState state = first.commit(new RevisionNumber(1L));
        final TableWriter second = TableWriter.open(blocks, state);
        second.add(TableWriterTest.numbered(99), new byte[4]);
        Assertions.assertEquals(
            List.of(false, false),
            List.of(
                state.sequential(),
                second.commit(new RevisionNumber(2L)).sequential()
            ),
            "Additions out of order stay recorded after a reopen"
        );
    }

    @Test
    void restoresOrderFlagOnCancel() {
        final TableWriter table = TableWriter.create(
            new MemoryBlocks(new HashMap<>(), BlockSize.MIN), 0
        );
        table.add(TableWriterTest.numbered(2), new byte[4]);
        table.add(TableWriterTest.numbered(1), new byte[4]);
        table.cancel(TableState.initial(BlockSize.MIN, 0));
        table.add(TableWriterTest.numbered(1), new byte[4]);
        table.add(TableWriterTest.numbered(2), new byte[4]);
        Assertions.assertTrue(
            table.commit(new RevisionNumber(1L)).sequential(),
            "Additions in order after a cancel"
        );
    }

    @Test
    void stampsChangedBlocksWithNewRevision() {
        final MemoryBlocks blocks = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        final TableWriter table = TableWriter.create(blocks, 0);
        table.add(TableWriterTest.numbered(1), new byte[4]);
        table.commit(new RevisionNumber(1L));
        table.add(TableWriterTest.numbered(2), new byte[4]);
        Assertions.assertEquals(
            2,
            ByteBuffer.wrap(
                blocks.bytes(
                    table.commit(new RevisionNumber(2L)).root().block().value()
                )
            ).getInt(0),
            "Root block carries the revision that wrote it"
        );
    }

    @Test
    void compressesOnlyAboveThreshold() {
        final MemoryBlocks blocks = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        final TableWriter table =
            TableWriter.create(blocks, TableWriterTest.COMPRESS);
        final byte[] exact = new byte[TableWriterTest.COMPRESS];
        Arrays.fill(exact, (byte) 'a');
        final byte[] above = new byte[TableWriterTest.COMPRESS + 1];
        Arrays.fill(above, (byte) 'a');
        table.add(Trees.key("a"), exact);
        table.add(Trees.key("b"), above);
        final TableState state = table.commit(new RevisionNumber(1L));
        final Block root = Block.parse(
            state.root().block(),
            blocks.bytes(state.root().block().value())
        );
        Assertions.assertEquals(
            List.of(false, true),
            List.of(root.compressed(1), root.compressed(2)),
            "Only the tag longer than the threshold is compressed"
        );
    }

    private static Class<?> addition(final TableWriter table) {
        Class<?> error = Object.class;
        try {
            table.add(Trees.key("b"), new byte[1]);
        } catch (final DatabaseClosedError ex) {
            error = ex.getClass();
        }
        return error;
    }

    private static String failure(final TableWriter table) {
        String message = "";
        try {
            table.commit(new RevisionNumber(1L));
        } catch (final DatabaseCorruptError ex) {
            message = ex.getMessage();
        }
        return message;
    }

    private static TableState fill(final List<byte[]> keys, final boolean full) {
        final TableWriter table = TableWriter.create(
            new MemoryBlocks(new HashMap<>(), BlockSize.MIN), 0
        );
        table.compaction(full);
        for (final byte[] key : keys) {
            table.add(key, new byte[20]);
        }
        return table.commit(new RevisionNumber(1L));
    }

    private static List<byte[]> keys(final int count) {
        final List<byte[]> keys = new ArrayList<>(count);
        for (int idx = 0; idx < count; ++idx) {
            keys.add(TableWriterTest.numbered(idx));
        }
        return keys;
    }

    private static byte[] numbered(final int num) {
        return Trees.key(String.format("key%06d", num));
    }
}
