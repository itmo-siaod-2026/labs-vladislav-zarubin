package org.example.performance;

import org.example.fileshash.FileHashTable;
import org.example.support.RandomDataFactory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 20, time = 1)
@Fork(5)
@Threads(1)
public class FileHashTableBenchmark {
    private static final int BUCKET_CAPACITY = 32;
    private static final int MAX_ITEM_COUNT = 12_000;
    private static final int MAX_INSERT_KEY_COUNT = MAX_ITEM_COUNT + JmhSupport.FILE_HASH_BATCH_OPERATIONS;
    private static final int HOT_SET_SIZE = 64;
    private static final int HOT_SET_LARGE_SIZE = 1024;
    private static final long READ_KEYS_SEED = 1_001L;
    private static final long READ_INDEXES_SEED = 17_001L;
    private static final long HOT_SET_INDEXES_SEED = 57_001L;
    private static final long HOT_SET_LARGE_INDEXES_SEED = 67_001L;
    private static final long UPDATE_KEYS_SEED = 2_001L;
    private static final long UPDATE_INDEXES_SEED = 27_001L;
    private static final long INSERT_KEYS_SEED = 3_001L;
    private static final long DELETE_KEYS_SEED = 4_001L;
    private static final long DELETE_INDEXES_SEED = 47_001L;
    private static final List<String> READ_KEY_POOL = RandomDataFactory.uniqueKeys(MAX_ITEM_COUNT, READ_KEYS_SEED);
    private static final List<String> UPDATE_KEY_POOL = RandomDataFactory.uniqueKeys(MAX_ITEM_COUNT, UPDATE_KEYS_SEED);
    private static final List<String> INSERT_KEY_POOL = RandomDataFactory.uniqueKeys(MAX_INSERT_KEY_COUNT, INSERT_KEYS_SEED);
    private static final List<String> DELETE_KEY_POOL = RandomDataFactory.uniqueKeys(MAX_ITEM_COUNT, DELETE_KEYS_SEED);

    @State(Scope.Benchmark)
    public static class ReadState {
        @Param({"2000", "4000", "6000", "8000", "10000", "12000"})
        public int itemCount;

        Path directory;
        FileHashTable table;
        List<String> keys;
        int[] queryIndexes;
        int[] hotSetIndexes;
        int[] hotSetLargeIndexes;

        @Setup(Level.Iteration)
        public void setUp() throws IOException {
            directory = Files.createTempDirectory("jmh-filehash-read");
            table = new FileHashTable(directory, BUCKET_CAPACITY);
            keys = READ_KEY_POOL.subList(0, itemCount);
            queryIndexes = JmhSupport.randomIndexes(itemCount, JmhSupport.FILE_HASH_BATCH_OPERATIONS, READ_INDEXES_SEED);
            hotSetIndexes = JmhSupport.randomIndexes(
                    Math.min(HOT_SET_SIZE, itemCount),
                    JmhSupport.FILE_HASH_BATCH_OPERATIONS,
                    HOT_SET_INDEXES_SEED
            );
            hotSetLargeIndexes = JmhSupport.randomIndexes(
                    Math.min(HOT_SET_LARGE_SIZE, itemCount),
                    JmhSupport.FILE_HASH_BATCH_OPERATIONS,
                    HOT_SET_LARGE_INDEXES_SEED
            );

            for (int index = 0; index < itemCount; index++) {
                table.insert(keys.get(index), "value-" + index);
            }
        }

        @TearDown(Level.Iteration)
        public void tearDown() throws IOException {
            if (table != null) {
                table.close();
                table = null;
            }
            JmhSupport.deleteRecursively(directory);
        }
    }

    @State(Scope.Benchmark)
    public static class UpdateState {
        @Param({"2000", "4000", "6000", "8000", "10000", "12000"})
        public int itemCount;

        Path directory;
        FileHashTable table;
        List<String> keys;
        int[] updateIndexes;
        String[] updatedValues;

        @Setup(Level.Iteration)
        public void setUp() throws IOException {
            directory = Files.createTempDirectory("jmh-filehash-update");
            table = new FileHashTable(directory, BUCKET_CAPACITY);
            keys = UPDATE_KEY_POOL.subList(0, itemCount);
            updateIndexes = JmhSupport.randomIndexes(itemCount, JmhSupport.FILE_HASH_BATCH_OPERATIONS, UPDATE_INDEXES_SEED);
            updatedValues = JmhSupport.stringValues("updated", JmhSupport.FILE_HASH_BATCH_OPERATIONS);

            for (int index = 0; index < itemCount; index++) {
                table.insert(keys.get(index), "value-" + index);
            }
        }

        @TearDown(Level.Iteration)
        public void tearDown() throws IOException {
            if (table != null) {
                table.close();
                table = null;
            }
            JmhSupport.deleteRecursively(directory);
        }
    }

    @State(Scope.Benchmark)
    public static class InsertState {
        @Param({"2000", "4000", "6000", "8000", "10000", "12000"})
        public int itemCount;

        Path directory;
        FileHashTable table;
        List<String> baseKeys;
        List<String> insertKeys;
        String[] insertedValues;

        @Setup(Level.Iteration)
        public void setUpData() {
            baseKeys = INSERT_KEY_POOL.subList(0, itemCount);
            insertKeys = INSERT_KEY_POOL.subList(itemCount, itemCount + JmhSupport.FILE_HASH_BATCH_OPERATIONS);
            insertedValues = JmhSupport.stringValues("inserted", JmhSupport.FILE_HASH_BATCH_OPERATIONS);
        }

        @Setup(Level.Invocation)
        public void setUpTable() throws IOException {
            directory = Files.createTempDirectory("jmh-filehash-insert");
            table = new FileHashTable(directory, BUCKET_CAPACITY);

            for (int index = 0; index < itemCount; index++) {
                table.insert(baseKeys.get(index), "value-" + index);
            }
        }

        @TearDown(Level.Invocation)
        public void tearDown() throws IOException {
            if (table != null) {
                table.close();
                table = null;
            }
            JmhSupport.deleteRecursively(directory);
        }
    }

    @State(Scope.Benchmark)
    public static class DeleteState {
        @Param({"2000", "4000", "6000", "8000", "10000", "12000"})
        public int itemCount;

        Path directory;
        FileHashTable table;
        List<String> keys;
        String[] deleteKeys;
        String[] deleteValues;

        @Setup(Level.Iteration)
        public void setUp() throws IOException {
            directory = Files.createTempDirectory("jmh-filehash-delete");
            table = new FileHashTable(directory, BUCKET_CAPACITY);
            keys = DELETE_KEY_POOL.subList(0, itemCount);
            int[] deleteIndexes = JmhSupport.uniqueRandomIndexes(
                    itemCount,
                    JmhSupport.FILE_HASH_BATCH_OPERATIONS,
                    DELETE_INDEXES_SEED
            );
            deleteKeys = new String[JmhSupport.FILE_HASH_BATCH_OPERATIONS];
            deleteValues = new String[JmhSupport.FILE_HASH_BATCH_OPERATIONS];

            for (int index = 0; index < itemCount; index++) {
                table.insert(keys.get(index), "value-" + index);
            }

            for (int position = 0; position < deleteIndexes.length; position++) {
                int keyIndex = deleteIndexes[position];
                deleteKeys[position] = keys.get(keyIndex);
                deleteValues[position] = "value-" + keyIndex;
            }
        }

        @TearDown(Level.Invocation)
        public void restoreDeletedKeys() throws IOException {
            if (table == null) {
                return;
            }
            for (int position = 0; position < deleteKeys.length; position++) {
                if (table.get(deleteKeys[position]) == null) {
                    table.insert(deleteKeys[position], deleteValues[position]);
                }
            }
        }

        @TearDown(Level.Iteration)
        public void tearDown() throws IOException {
            if (table != null) {
                table.close();
                table = null;
            }
            JmhSupport.deleteRecursively(directory);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @OperationsPerInvocation(JmhSupport.FILE_HASH_BATCH_OPERATIONS)
    public void getExistingBatch(ReadState state, Blackhole blackhole) throws IOException {
        runGetBatch(state, blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @OperationsPerInvocation(JmhSupport.FILE_HASH_BATCH_OPERATIONS)
    public void getExistingThroughput(ReadState state, Blackhole blackhole) throws IOException {
        runGetBatch(state, blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @OperationsPerInvocation(JmhSupport.FILE_HASH_BATCH_OPERATIONS)
    public void getHotSetBatch(ReadState state, Blackhole blackhole) throws IOException {
        runHotSetGetBatch(state, blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @OperationsPerInvocation(JmhSupport.FILE_HASH_BATCH_OPERATIONS)
    public void getHotSetThroughput(ReadState state, Blackhole blackhole) throws IOException {
        runHotSetGetBatch(state, blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @OperationsPerInvocation(JmhSupport.FILE_HASH_BATCH_OPERATIONS)
    public void getHotSetLargeBatch(ReadState state, Blackhole blackhole) throws IOException {
        runHotSetLargeGetBatch(state, blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @OperationsPerInvocation(JmhSupport.FILE_HASH_BATCH_OPERATIONS)
    public void getHotSetLargeThroughput(ReadState state, Blackhole blackhole) throws IOException {
        runHotSetLargeGetBatch(state, blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @OperationsPerInvocation(JmhSupport.FILE_HASH_BATCH_OPERATIONS)
    public void updateExistingBatch(UpdateState state) throws IOException {
        runUpdateBatch(state);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @OperationsPerInvocation(JmhSupport.FILE_HASH_BATCH_OPERATIONS)
    public void updateExistingThroughput(UpdateState state) throws IOException {
        runUpdateBatch(state);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @OperationsPerInvocation(JmhSupport.FILE_HASH_BATCH_OPERATIONS)
    public void insertFreshBatch(InsertState state) throws IOException {
        runInsertBatch(state);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @OperationsPerInvocation(JmhSupport.FILE_HASH_BATCH_OPERATIONS)
    public void insertFreshThroughput(InsertState state) throws IOException {
        runInsertBatch(state);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @OperationsPerInvocation(JmhSupport.FILE_HASH_BATCH_OPERATIONS)
    public void deleteExistingBatch(DeleteState state) throws IOException {
        runDeleteBatch(state);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @OperationsPerInvocation(JmhSupport.FILE_HASH_BATCH_OPERATIONS)
    public void deleteExistingThroughput(DeleteState state) throws IOException {
        runDeleteBatch(state);
    }

    private static void runGetBatch(ReadState state, Blackhole blackhole) throws IOException {
        for (int index : state.queryIndexes) {
            blackhole.consume(state.table.get(state.keys.get(index)));
        }
    }

    private static void runHotSetGetBatch(ReadState state, Blackhole blackhole) throws IOException {
        for (int index : state.hotSetIndexes) {
            blackhole.consume(state.table.get(state.keys.get(index)));
        }
    }

    private static void runHotSetLargeGetBatch(ReadState state, Blackhole blackhole) throws IOException {
        for (int index : state.hotSetLargeIndexes) {
            blackhole.consume(state.table.get(state.keys.get(index)));
        }
    }

    private static void runUpdateBatch(UpdateState state) throws IOException {
        for (int position = 0; position < state.updateIndexes.length; position++) {
            state.table.update(
                    state.keys.get(state.updateIndexes[position]),
                    state.updatedValues[position]
            );
        }
    }

    private static void runInsertBatch(InsertState state) throws IOException {
        for (int index = 0; index < JmhSupport.FILE_HASH_BATCH_OPERATIONS; index++) {
            state.table.insert(
                    state.insertKeys.get(index),
                    state.insertedValues[index]
            );
        }
    }

    private static void runDeleteBatch(DeleteState state) throws IOException {
        for (String key : state.deleteKeys) {
            state.table.delete(key);
        }
    }
}
