package org.example.performance;

import org.example.index.RamIndex;
import org.example.search.Finder;
import org.example.store.DiskIndex;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 15, time = 1)
@Fork(value = 2, jvmArgsAppend = "-Xmx4g")
@Threads(1)
public class DiskBench {

    @State(Scope.Benchmark)
    public static class DiskQTreeState {
        @Param({"10000", "30000", "100000"})
        public int numDocs;

        Path directory;
        DiskIndex index;
        Finder engine;
        QueryPairs highLow;
        String[] termQ;

        @Setup(Level.Trial)
        public void setUp() throws IOException {
            RamIndex memory = MockDocs.buildIndex(MockDocs.documents(
                    numDocs, BenchCfg.DOC_LENGTH, BenchCfg.VOCAB_SIZE, 12_000L + numDocs));
            directory = Files.createTempDirectory("jmh-inverted-index");
            Path file = directory.resolve("index.idx");
            DiskIndex.write(memory, file);
            index = DiskIndex.open(file);
            engine = new Finder(index);
            int batch = BenchCfg.QUERY_BATCH;
            int vocab = BenchCfg.VOCAB_SIZE;
            long base = 20_000L + numDocs;
            highLow = MockDocs.highLow(batch, vocab, base);
            termQ = MockDocs.termQueries(batch, vocab, base + 1);
        }

        @TearDown(Level.Trial)
        public void tearDown() throws IOException {
            if (index != null) {
                index.close();
            }
            BenchCfg.deleteRecursively(directory);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @OperationsPerInvocation(BenchCfg.QUERY_BATCH)
    public void diskRankedTerm(DiskQTreeState state, Blackhole blackhole) {
        runSearch(state, state.termQ, blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @OperationsPerInvocation(BenchCfg.QUERY_BATCH)
    public void diskRankedTermThroughput(DiskQTreeState state, Blackhole blackhole) {
        runSearch(state, state.termQ, blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @OperationsPerInvocation(BenchCfg.QUERY_BATCH)
    public void diskRankedAnd(DiskQTreeState state, Blackhole blackhole) {
        runSearch(state, state.highLow.asAnd(), blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @OperationsPerInvocation(BenchCfg.QUERY_BATCH)
    public void diskRankedAndThroughput(DiskQTreeState state, Blackhole blackhole) {
        runSearch(state, state.highLow.asAnd(), blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @OperationsPerInvocation(BenchCfg.QUERY_BATCH)
    public void diskRankedOr(DiskQTreeState state, Blackhole blackhole) {
        runSearch(state, state.highLow.asOr(), blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @OperationsPerInvocation(BenchCfg.QUERY_BATCH)
    public void diskRankedOrThroughput(DiskQTreeState state, Blackhole blackhole) {
        runSearch(state, state.highLow.asOr(), blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @OperationsPerInvocation(BenchCfg.QUERY_BATCH)
    public void diskRankedNot(DiskQTreeState state, Blackhole blackhole) {
        runSearch(state, state.highLow.asAndNot(), blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @OperationsPerInvocation(BenchCfg.QUERY_BATCH)
    public void diskRankedNotThroughput(DiskQTreeState state, Blackhole blackhole) {
        runSearch(state, state.highLow.asAndNot(), blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @OperationsPerInvocation(BenchCfg.QUERY_BATCH)
    public void diskRankedAdj(DiskQTreeState state, Blackhole blackhole) {
        runSearch(state, state.highLow.asAdj(), blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @OperationsPerInvocation(BenchCfg.QUERY_BATCH)
    public void diskRankedAdjThroughput(DiskQTreeState state, Blackhole blackhole) {
        runSearch(state, state.highLow.asAdj(), blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @OperationsPerInvocation(BenchCfg.QUERY_BATCH)
    public void diskRankedNear(DiskQTreeState state, Blackhole blackhole) {
        runSearch(state, state.highLow.asNear(5), blackhole);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @OperationsPerInvocation(BenchCfg.QUERY_BATCH)
    public void diskRankedNearThroughput(DiskQTreeState state, Blackhole blackhole) {
        runSearch(state, state.highLow.asNear(5), blackhole);
    }

    private static void runSearch(DiskQTreeState state, String[] queries, Blackhole blackhole) {
        for (String query : queries) {
            blackhole.consume(state.engine.search(query, 10));
        }
    }
}
