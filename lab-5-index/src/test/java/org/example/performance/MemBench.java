package org.example.performance;

import org.example.search.Finder;
import org.example.support.MockDocs;
import org.example.support.MockDocs.QueryPairs;
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

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 15, time = 1)
@Fork(value = 2, jvmArgsAppend = "-Xmx4g")
@Threads(1)
public class MemBench {

    @State(Scope.Benchmark)
    public static class QTreeState {
        @Param({"10000", "30000", "100000"})
        public int numDocs;

        Finder engine;
        QueryPairs highLow;
        String[] termQ;

        @Setup(Level.Trial)
        public void setUp() {
            engine = new Finder(MockDocs.buildIndex(MockDocs.documents(
                    numDocs, BenchCfg.DOC_LENGTH, BenchCfg.VOCAB_SIZE, 9_000L + numDocs)));
            int batch = BenchCfg.QUERY_BATCH;
            int vocab = BenchCfg.VOCAB_SIZE;
            long base = 10_000L + numDocs;
            highLow = MockDocs.highLow(batch, vocab, base);
            termQ = MockDocs.termQueries(batch, vocab, base + 1);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @OperationsPerInvocation(BenchCfg.QUERY_BATCH)
    public void rankedTerm(QTreeState state, Blackhole blackhole) {
        runSearch(state, state.termQ, blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @OperationsPerInvocation(BenchCfg.QUERY_BATCH)
    public void rankedTermThroughput(QTreeState state, Blackhole blackhole) {
        runSearch(state, state.termQ, blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @OperationsPerInvocation(BenchCfg.QUERY_BATCH)
    public void rankedAnd(QTreeState state, Blackhole blackhole) {
        runSearch(state, state.highLow.asAnd(), blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @OperationsPerInvocation(BenchCfg.QUERY_BATCH)
    public void rankedAndThroughput(QTreeState state, Blackhole blackhole) {
        runSearch(state, state.highLow.asAnd(), blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @OperationsPerInvocation(BenchCfg.QUERY_BATCH)
    public void rankedOr(QTreeState state, Blackhole blackhole) {
        runSearch(state, state.highLow.asOr(), blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @OperationsPerInvocation(BenchCfg.QUERY_BATCH)
    public void rankedOrThroughput(QTreeState state, Blackhole blackhole) {
        runSearch(state, state.highLow.asOr(), blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @OperationsPerInvocation(BenchCfg.QUERY_BATCH)
    public void rankedNot(QTreeState state, Blackhole blackhole) {
        runSearch(state, state.highLow.asAndNot(), blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @OperationsPerInvocation(BenchCfg.QUERY_BATCH)
    public void rankedNotThroughput(QTreeState state, Blackhole blackhole) {
        runSearch(state, state.highLow.asAndNot(), blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @OperationsPerInvocation(BenchCfg.QUERY_BATCH)
    public void rankedAdj(QTreeState state, Blackhole blackhole) {
        runSearch(state, state.highLow.asAdj(), blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @OperationsPerInvocation(BenchCfg.QUERY_BATCH)
    public void rankedAdjThroughput(QTreeState state, Blackhole blackhole) {
        runSearch(state, state.highLow.asAdj(), blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @OperationsPerInvocation(BenchCfg.QUERY_BATCH)
    public void rankedNear(QTreeState state, Blackhole blackhole) {
        runSearch(state, state.highLow.asNear(5), blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @OperationsPerInvocation(BenchCfg.QUERY_BATCH)
    public void rankedNearThroughput(QTreeState state, Blackhole blackhole) {
        runSearch(state, state.highLow.asNear(5), blackhole);
    }

    private static void runSearch(QTreeState state, String[] queries, Blackhole blackhole) {
        for (String query : queries) {
            blackhole.consume(state.engine.search(query, 10));
        }
    }
}
