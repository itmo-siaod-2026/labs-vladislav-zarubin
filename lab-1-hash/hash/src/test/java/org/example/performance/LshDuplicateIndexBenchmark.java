package org.example.performance;

import org.example.lsh.LshDuplicateIndex;
import org.example.lsh.LshDuplicateIndex.DuplicateSearchResult;
import org.example.lsh.LshDuplicateIndex.Point3D;
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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 20, time = 1)
@Fork(5)
@Threads(1)
public class LshDuplicateIndexBenchmark {
    private static final int NUM_HASH_FUNCTIONS = 8;

    @State(Scope.Benchmark)
    public static class BuildState {
        @Param({"2000", "4000", "6000", "8000", "10000", "12000"})
        public int itemCount;

        List<Point3D> points;

        @Setup(Level.Iteration)
        public void setUp() {
            points = JmhSupport.pointsDataset(itemCount, 60_001L + itemCount);
        }
    }

    @State(Scope.Benchmark)
    public static class AddState {
        @Param({"2000", "4000", "6000", "8000", "10000", "12000"})
        public int itemCount;

        LshDuplicateIndex index;
        List<Point3D> extraPoints;

        @Setup(Level.Invocation)
        public void setUp() {
            index = new LshDuplicateIndex(NUM_HASH_FUNCTIONS, 70_001L + itemCount);
            index.addAll(JmhSupport.pointsDataset(itemCount, 80_001L + itemCount));
            extraPoints = JmhSupport.randomPoints("new-", JmhSupport.LSH_ADD_BATCH_OPERATIONS, 90_001L + itemCount);
        }
    }

    @State(Scope.Benchmark)
    public static class SearchState {
        @Param({"2000", "4000", "6000", "8000", "10000", "12000"})
        public int itemCount;

        LshDuplicateIndex index;

        @Setup(Level.Iteration)
        public void setUp() {
            index = new LshDuplicateIndex(NUM_HASH_FUNCTIONS, 100_001L + itemCount);
            index.addAll(JmhSupport.pointsDataset(itemCount, 110_001L + itemCount));
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public LshDuplicateIndex buildIndex(BuildState state) {
        return buildIndexImpl(state);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public LshDuplicateIndex buildIndexThroughput(BuildState state) {
        return buildIndexImpl(state);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @OperationsPerInvocation(JmhSupport.LSH_ADD_BATCH_OPERATIONS)
    public void addPointsBatch(AddState state) {
        runAddPointsBatch(state);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @OperationsPerInvocation(JmhSupport.LSH_ADD_BATCH_OPERATIONS)
    public void addPointsThroughput(AddState state) {
        runAddPointsBatch(state);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void lshSearch(SearchState state, Blackhole blackhole) {
        runLshSearch(state, blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void lshSearchThroughput(SearchState state, Blackhole blackhole) {
        runLshSearch(state, blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void fullScan(SearchState state, Blackhole blackhole) {
        runFullScan(state, blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void fullScanThroughput(SearchState state, Blackhole blackhole) {
        runFullScan(state, blackhole);
    }

    private static LshDuplicateIndex buildIndexImpl(BuildState state) {
        LshDuplicateIndex index = new LshDuplicateIndex(NUM_HASH_FUNCTIONS, 140_001L + state.itemCount);
        index.addAll(state.points);
        return index;
    }

    private static void runAddPointsBatch(AddState state) {
        for (Point3D point : state.extraPoints) {
            state.index.add(point);
        }
    }

    private static void runLshSearch(SearchState state, Blackhole blackhole) {
        DuplicateSearchResult result = state.index.findDoubles();
        blackhole.consume(result);
    }

    private static void runFullScan(SearchState state, Blackhole blackhole) {
        DuplicateSearchResult result = state.index.fullScanDuplicates();
        blackhole.consume(result);
    }
}
