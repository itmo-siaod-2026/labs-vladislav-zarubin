package org.example.lsh;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class LshDuplicateIndex {
    private final int numHashFunctions;
    private final double[][] planes;
    private final Map<Long, List<Integer>> table = new HashMap<>();
    private final List<Point3D> points = new ArrayList<>();

    public LshDuplicateIndex(int numHashFunctions, long seed) {
        this.numHashFunctions   = numHashFunctions;
        this.planes = new double[numHashFunctions][3];

        Random random = new Random(seed);
        for (int planeIndex = 0; planeIndex < numHashFunctions; planeIndex++) {
            planes[planeIndex][0] = random.nextDouble() * 2.0 - 1.0;
            planes[planeIndex][1] = random.nextDouble() * 2.0 - 1.0;
            planes[planeIndex][2] = random.nextDouble() * 2.0 - 1.0;
        }
    }

    public void add(Point3D point) {
        int pointIndex = points.size();
        points.add(point);
        long hash = computeHash(point);
        table.computeIfAbsent(hash, ignored -> new ArrayList<>()).add(pointIndex);
    }

    public void addAll(Collection<Point3D> points) {
        for (Point3D point : points) {
            add(point);
        }
    }

    public DuplicateSearchResult findDoubles() {
        ArrayList<DuplicatePair> duplicates = new ArrayList<>();
        long distanceComparisons = 0L;

        for (List<Integer> bucket : table.values()) {
            for (int leftPosition = 0; leftPosition < bucket.size(); leftPosition++) {
                Point3D left = points.get(bucket.get(leftPosition));
                for (int rightPosition = leftPosition + 1; rightPosition < bucket.size(); rightPosition++) {
                    Point3D right = points.get(bucket.get(rightPosition));
                    distanceComparisons++;
                    if (samePoint(left, right)) {
                        duplicates.add(DuplicatePair.of(left, right, 0.0));
                    }
                }
            }
        }

        return new DuplicateSearchResult(duplicates, distanceComparisons);
    }

    public DuplicateSearchResult fullScanDuplicates() {
        ArrayList<DuplicatePair> duplicates = new ArrayList<>();
        long distanceComparisons = 0L;

        for (int leftIndex = 0; leftIndex < points.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < points.size(); rightIndex++) {
                Point3D left = points.get(leftIndex);
                Point3D right = points.get(rightIndex);
                distanceComparisons++;
                if (samePoint(left, right)) {
                    duplicates.add(DuplicatePair.of(left, right, 0.0));
                }
            }
        }

        return new DuplicateSearchResult(duplicates, distanceComparisons);
    }

    public int size() {
        return points.size();
    }

    private long computeHash(Point3D point) {
        long hash = 0L;
        for (int planeIndex = 0; planeIndex < numHashFunctions; planeIndex++) {
            double dot = point.x * planes[planeIndex][0]
                    + point.y * planes[planeIndex][1]
                    + point.z * planes[planeIndex][2];
            if (dot >= 0.0) {
                hash |= 1L << planeIndex;
            }
        }
        return hash;
    }

    private boolean samePoint(Point3D first, Point3D second) {
        return first.x == second.x
                && first.y == second.y
                && first.z == second.z;
    }

    public static class Point3D {
        public final String id;
        public final double x;
        public final double y;
        public final double z;

        public Point3D(String id, double x, double y, double z) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static class DuplicatePair {
        public final String leftId;
        public final String rightId;
        public final double distance;

        public DuplicatePair(String leftId, String rightId, double distance) {
            this.leftId = leftId;
            this.rightId = rightId;
            this.distance = distance;
        }

        public static DuplicatePair of(Point3D first, Point3D second, double distance) {
            return new DuplicatePair(first.id, second.id, distance);
        }

        public String pairKey() {
            return leftId + "->" + rightId;
        }
    }

    public static class DuplicateSearchResult {
        public final List<DuplicatePair> duplicates;
        public final long distanceComparisons;

        public DuplicateSearchResult(List<DuplicatePair> duplicates, long distanceComparisons) {
            this.duplicates = duplicates;
            this.distanceComparisons = distanceComparisons;
        }
    }
}
