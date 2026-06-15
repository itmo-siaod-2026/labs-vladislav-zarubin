package org.example.performance;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class BenchmarkRunner {
    public static void main(String[] args) throws Exception {
        runAt(1, "results-t1.csv", new String[]{"SIMPLE", "CONCURRENT", "JDK"});
        runAt(4, "results-t4.csv", new String[]{"CONCURRENT", "JDK"});
        runAt(8, "results-t8.csv", new String[]{"CONCURRENT", "JDK"});
    }

    private static void runAt(int threads, String resultFile, String[] impls) throws Exception {
        new Runner(new OptionsBuilder()
                .include(HashMapBenchmark.class.getSimpleName())
                .threads(threads)
                .param("impl", impls)
                .resultFormat(ResultFormatType.CSV)
                .result(resultFile)
                .build()).run();
    }
}
