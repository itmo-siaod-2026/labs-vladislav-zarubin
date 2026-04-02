package geosearch.benchmark;

import geosearch.GeoPoint;
import geosearch.NaiveGeoIndex;
import geosearch.Quadtree;
import geosearch.RandomDataGenerator;
import java.util.List;
import java.util.concurrent.TimeUnit;
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

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(2)
public class GeoIndexBenchmark {

    @State(Scope.Thread)
    public static class BuildState {
        @Param({"10000", "100000", "300000"})
        public int size;

        public List<GeoPoint> points;

        @Setup(Level.Trial)
        public void setup() {
            points = RandomDataGenerator.generatePoints(size, 1_000L + size);
        }
    }

    @State(Scope.Benchmark)
    public static class SearchState {
        public static final int QUERY_COUNT = 500;

        @Param({"10000", "100000", "1000000"})
        public int size;

        @Param({"0.1", "1.0", "10.0"})
        public double radius;

        public Quadtree quadtree;
        public NaiveGeoIndex naive;
        public List<GeoPoint> queryPoints;

        @Setup(Level.Trial)
        public void setup() {
            List<GeoPoint> points = RandomDataGenerator.generatePoints(size, 2_000L + size);
            queryPoints = RandomDataGenerator.generatePoints(QUERY_COUNT, 3_000L + size);

            quadtree = new Quadtree();
            naive = new NaiveGeoIndex();

            for (GeoPoint point : points) {
                quadtree.insert(point);
                naive.insert(point);
            }
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public int buildQuadtree(BuildState state) {
        Quadtree quadtree = new Quadtree();
        for (GeoPoint point : state.points) {
            quadtree.insert(point);
        }
        return quadtree.size();
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public int buildNaive(BuildState state) {
        NaiveGeoIndex naive = new NaiveGeoIndex();
        for (GeoPoint point : state.points) {
            naive.insert(point);
        }
        return naive.size();
    }

    @Benchmark
    @OperationsPerInvocation(SearchState.QUERY_COUNT)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public int searchQuadtreeAverage(SearchState state) {
        return runQuadtreeSearch(state);
    }

    @Benchmark
    @OperationsPerInvocation(SearchState.QUERY_COUNT)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public int searchNaiveAverage(SearchState state) {
        return runNaiveSearch(state);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OperationsPerInvocation(SearchState.QUERY_COUNT)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public int searchQuadtreeThroughput(SearchState state) {
        return runQuadtreeSearch(state);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OperationsPerInvocation(SearchState.QUERY_COUNT)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public int searchNaiveThroughput(SearchState state) {
        return runNaiveSearch(state);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            org.openjdk.jmh.Main.main(new String[]{"GeoIndexBenchmark"});
            return;
        }
        org.openjdk.jmh.Main.main(args);
    }

    private static int runQuadtreeSearch(SearchState state) {
        int total = 0;
        for (GeoPoint point : state.queryPoints) {
            total += state.quadtree.searchInRadius(point.lat, point.lng, state.radius).size();
        }
        return total;
    }

    private static int runNaiveSearch(SearchState state) {
        int total = 0;
        for (GeoPoint point : state.queryPoints) {
            total += state.naive.searchInRadius(point.lat, point.lng, state.radius).size();
        }
        return total;
    }
}
