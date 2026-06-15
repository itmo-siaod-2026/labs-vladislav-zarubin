package org.example.performance;

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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class HashMapBenchmark {

    static final int BATCH = 1024;

    private static final long KEYS_SEED = 1001L;
    private static final long VALUES_SEED = 2001L;
    private static final long OPS_SEED = 3001L;

    @Param({"10000", "100000", "1000000"})
    public int itemCount;

    @Param({"SIMPLE", "CONCURRENT", "JDK"})
    public String impl;

    private MapAdapter<Integer, Integer> map;
    private Integer[] keys;
    private Integer[] values;
    private boolean[] isPut;

    @Setup(Level.Trial)
    public void setUp() {
        map = MapAdapter.create(impl);
        for (int i = 0; i < itemCount; i++) map.put(i, i);

        Random rk = new Random(KEYS_SEED);
        Random rv = new Random(VALUES_SEED);
        Random ro = new Random(OPS_SEED);
        keys = new Integer[BATCH];
        values = new Integer[BATCH];
        isPut = new boolean[BATCH];
        for (int i = 0; i < BATCH; i++) {
            keys[i] = rk.nextInt(itemCount);
            values[i] = rv.nextInt();
            isPut[i] = ro.nextInt(5) == 0;
        }
    }

    private void doGet(Blackhole bh) {
        for (int i = 0; i < BATCH; i++) bh.consume(map.get(keys[i]));
    }

    private void doPut(Blackhole bh) {
        for (int i = 0; i < BATCH; i++) bh.consume(map.put(keys[i], values[i]));
    }

    private void doMerge(Blackhole bh) {
        for (int i = 0; i < BATCH; i++) bh.consume(map.merge(keys[i], 1, Integer::sum));
    }

    private void doMixed(Blackhole bh) {
        for (int i = 0; i < BATCH; i++) {
            if (isPut[i]) bh.consume(map.put(keys[i], values[i]));
            else bh.consume(map.get(keys[i]));
        }
    }

    private void doIterate(Blackhole bh) {
        Iterator<Map.Entry<Integer, Integer>> it = map.iterator();
        while (it.hasNext()) bh.consume(it.next());
    }

    // --- Throughput (ops/sec) ---

    @Benchmark @BenchmarkMode(Mode.Throughput) @OutputTimeUnit(TimeUnit.SECONDS) @OperationsPerInvocation(BATCH)
    public void getThroughput(Blackhole bh) { doGet(bh); }

    @Benchmark @BenchmarkMode(Mode.Throughput) @OutputTimeUnit(TimeUnit.SECONDS) @OperationsPerInvocation(BATCH)
    public void putThroughput(Blackhole bh) { doPut(bh); }

    @Benchmark @BenchmarkMode(Mode.Throughput) @OutputTimeUnit(TimeUnit.SECONDS) @OperationsPerInvocation(BATCH)
    public void mergeThroughput(Blackhole bh) { doMerge(bh); }

    @Benchmark @BenchmarkMode(Mode.Throughput) @OutputTimeUnit(TimeUnit.SECONDS) @OperationsPerInvocation(BATCH)
    public void mixedThroughput(Blackhole bh) { doMixed(bh); }

    @Benchmark @BenchmarkMode(Mode.Throughput) @OutputTimeUnit(TimeUnit.SECONDS)
    public void iteratorThroughput(Blackhole bh) { doIterate(bh); }

    // --- AverageTime (time per op) ---

    @Benchmark @BenchmarkMode(Mode.AverageTime) @OutputTimeUnit(TimeUnit.NANOSECONDS) @OperationsPerInvocation(BATCH)
    public void getAvg(Blackhole bh) { doGet(bh); }

    @Benchmark @BenchmarkMode(Mode.AverageTime) @OutputTimeUnit(TimeUnit.NANOSECONDS) @OperationsPerInvocation(BATCH)
    public void putAvg(Blackhole bh) { doPut(bh); }

    @Benchmark @BenchmarkMode(Mode.AverageTime) @OutputTimeUnit(TimeUnit.NANOSECONDS) @OperationsPerInvocation(BATCH)
    public void mergeAvg(Blackhole bh) { doMerge(bh); }

    @Benchmark @BenchmarkMode(Mode.AverageTime) @OutputTimeUnit(TimeUnit.NANOSECONDS) @OperationsPerInvocation(BATCH)
    public void mixedAvg(Blackhole bh) { doMixed(bh); }

    @Benchmark @BenchmarkMode(Mode.AverageTime) @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void iteratorAvg(Blackhole bh) { doIterate(bh); }
}
