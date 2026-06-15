package org.example.performance;

import org.example.perfecthash.PerfectHashIndex;
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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 20, time = 1)
@Fork(5)
@Threads(1)
public class PerfectHashIndexBenchmark {
    @State(Scope.Benchmark)
    public static class BuildState {
        @Param({"20000", "40000", "60000", "80000", "100000", "120000"})
        public int itemCount;

        List<String> keys;

        @Setup(Level.Iteration)
        public void setUp() {
            keys = RandomDataFactory.uniqueKeys(itemCount, 10_001L + itemCount);
        }
    }

    @State(Scope.Benchmark)
    public static class LookupState {
        @Param({"20000", "40000", "60000", "80000", "100000", "120000"})
        public int itemCount;

        List<String> keys;
        List<String> missingKeys;
        PerfectHashIndex index;
        int[] hitIndexes;
        int[] missIndexes;

        @Setup(Level.Iteration)
        public void setUp() {
            keys = RandomDataFactory.uniqueKeys(itemCount, 20_001L + itemCount);
            missingKeys = RandomDataFactory.missingKeys(itemCount, 30_001L + itemCount);
            index = PerfectHashIndex.fromKeys(keys);
            hitIndexes = JmhSupport.randomIndexes(itemCount, JmhSupport.PERFECT_HASH_LOOKUPS_PER_INVOCATION, 40_001L + itemCount);
            missIndexes = JmhSupport.randomIndexes(itemCount, JmhSupport.PERFECT_HASH_LOOKUPS_PER_INVOCATION, 50_001L + itemCount);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public PerfectHashIndex buildIndex(BuildState state) {
        return buildIndexImpl(state);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public PerfectHashIndex buildIndexThroughput(BuildState state) {
        return buildIndexImpl(state);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @OperationsPerInvocation(JmhSupport.PERFECT_HASH_LOOKUPS_PER_INVOCATION)
    public void lookupHitBatch(LookupState state, Blackhole blackhole) {
        runLookupBatch(state.index, state.keys, state.hitIndexes, blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @OperationsPerInvocation(JmhSupport.PERFECT_HASH_LOOKUPS_PER_INVOCATION)
    public void lookupHitThroughput(LookupState state, Blackhole blackhole) {
        runLookupBatch(state.index, state.keys, state.hitIndexes, blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @OperationsPerInvocation(JmhSupport.PERFECT_HASH_LOOKUPS_PER_INVOCATION)
    public void lookupMissBatch(LookupState state, Blackhole blackhole) {
        runLookupBatch(state.index, state.missingKeys, state.missIndexes, blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @OperationsPerInvocation(JmhSupport.PERFECT_HASH_LOOKUPS_PER_INVOCATION)
    public void lookupMissThroughput(LookupState state, Blackhole blackhole) {
        runLookupBatch(state.index, state.missingKeys, state.missIndexes, blackhole);
    }

    private static PerfectHashIndex buildIndexImpl(BuildState state) {
        return PerfectHashIndex.fromKeys(state.keys);
    }

    private static void runLookupBatch(
            PerfectHashIndex index,
            List<String> lookupKeys,
            int[] lookupIndexes,
            Blackhole blackhole
    ) {
        for (int lookupIndex : lookupIndexes) {
            blackhole.consume(index.find(lookupKeys.get(lookupIndex)));
        }
    }
}
