package org.example.performance;

import org.example.lsh.LshDuplicateIndex.Point3D;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

final class JmhSupport {
    private JmhSupport() {
    }

    static final int FILE_HASH_BATCH_OPERATIONS = 1_024;
    static final int PERFECT_HASH_LOOKUPS_PER_INVOCATION = 4_096;
    static final int LSH_ADD_BATCH_OPERATIONS = 512;

    static int[] randomIndexes(int upperBoundExclusive, int count, long seed) {
        Random random = new Random(seed);
        int[] indexes = new int[count];
        for (int index = 0; index < count; index++) {
            indexes[index] = random.nextInt(upperBoundExclusive);
        }
        return indexes;
    }

    static int[] uniqueRandomIndexes(int upperBoundExclusive, int count, long seed) {
        if (count > upperBoundExclusive) {
            throw new IllegalArgumentException("count must not exceed upperBoundExclusive");
        }

        Random random = new Random(seed);
        int[] shuffled = new int[upperBoundExclusive];
        for (int index = 0; index < upperBoundExclusive; index++) {
            shuffled[index] = index;
        }

        for (int index = 0; index < count; index++) {
            int swapIndex = index + random.nextInt(upperBoundExclusive - index);
            int value = shuffled[index];
            shuffled[index] = shuffled[swapIndex];
            shuffled[swapIndex] = value;
        }

        int[] uniqueIndexes = new int[count];
        System.arraycopy(shuffled, 0, uniqueIndexes, 0, count);
        return uniqueIndexes;
    }

    static String[] stringValues(String prefix, int count) {
        String[] values = new String[count];
        for (int index = 0; index < count; index++) {
            values[index] = prefix + '-' + index;
        }
        return values;
    }

    static List<Point3D> pointsDataset(int totalCount, long seed) {
        int duplicateGroups = Math.max(1, totalCount / 10);
        int uniqueCount = Math.max(1, totalCount - duplicateGroups);

        Random random = new Random(seed);
        ArrayList<Point3D> points = new ArrayList<>(totalCount);

        for (int index = 0; index < uniqueCount; index++) {
            points.add(new Point3D(
                    "p" + index,
                    random.nextDouble() * 1_000.0,
                    random.nextDouble() * 1_000.0,
                    random.nextDouble() * 1_000.0
            ));
        }

        for (int group = 0; group < duplicateGroups && points.size() < totalCount; group++) {
            Point3D base = points.get(random.nextInt(points.size()));
            points.add(new Point3D(
                    "dup" + group,
                    base.x,
                    base.y,
                    base.z
            ));
        }

        return points;
    }

    static List<Point3D> randomPoints(String prefix, int count, long seed) {
        Random random = new Random(seed);
        ArrayList<Point3D> points = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            points.add(new Point3D(
                    prefix + index,
                    random.nextDouble() * 10_000.0,
                    random.nextDouble() * 10_000.0,
                    random.nextDouble() * 10_000.0
            ));
        }
        return points;
    }

    static void deleteRecursively(Path root) throws IOException {
        if (root == null || Files.notExists(root)) {
            return;
        }

        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    });
        } catch (RuntimeException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
    }

}
