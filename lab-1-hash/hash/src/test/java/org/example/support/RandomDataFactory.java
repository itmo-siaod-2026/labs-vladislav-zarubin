package org.example.support;

import org.example.lsh.LshDuplicateIndex.Point3D;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public final class RandomDataFactory {
    private RandomDataFactory() {
    }

    public static List<String> uniqueKeys(int count, long seed) {
        Random random = new Random(seed);
        String[] result = new String[count];

        int capacity = Integer.highestOneBit(Math.max(16, count * 2 - 1)) << 1;
        int mask = capacity - 1;
        String[] dedup = new String[capacity];

        int filled = 0;
        while (filled < count) {
            String candidate = randomString(random, 12);
            int slot = (candidate.hashCode() & 0x7FFFFFFF) & mask;
            boolean duplicate = false;
            while (dedup[slot] != null) {
                if (dedup[slot].equals(candidate)) {
                    duplicate = true;
                    break;
                }
                slot = (slot + 1) & mask;
            }
            if (duplicate) {
                continue;
            }
            dedup[slot] = candidate;
            result[filled++] = candidate;
        }

        return Arrays.asList(result);
    }

    public static List<String> missingKeys(int count, long seed) {
        return uniqueKeys(count, seed + 1_000_000L);
    }

    public static List<Point3D> pointsWithDuplicates(int uniqueCount, int duplicateGroups, double jitter, long seed) {
        Random random = new Random(seed);
        ArrayList<Point3D> points = new ArrayList<>(uniqueCount + duplicateGroups);

        for (int index = 0; index < uniqueCount; index++) {
            double x = random.nextDouble() * 1_000.0;
            double y = random.nextDouble() * 1_000.0;
            double z = random.nextDouble() * 1_000.0;
            points.add(new Point3D("p" + index, x, y, z));
        }

        for (int group = 0; group < duplicateGroups; group++) {
            Point3D base = points.get(random.nextInt(points.size()));
            points.add(new Point3D(
                    "dup" + group,
                    base.x + centeredNoise(random, jitter),
                    base.y + centeredNoise(random, jitter),
                    base.z + centeredNoise(random, jitter)
            ));
        }

        return points;
    }

    public static List<Point3D> randomPoints(int count, long seed) {
        Random random = new Random(seed);
        ArrayList<Point3D> points = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            points.add(new Point3D(
                    "p" + index,
                    random.nextDouble() * 10_000.0,
                    random.nextDouble() * 10_000.0,
                    random.nextDouble() * 10_000.0
            ));
        }
        return points;
    }

    private static String randomString(Random random, int length) {
        char[] chars = new char[length];
        for (int index = 0; index < length; index++) {
            chars[index] = (char) ('a' + random.nextInt(26));
        }
        return new String(chars);
    }

    private static double centeredNoise(Random random, double amplitude) {
        return (random.nextDouble() - 0.5) * amplitude;
    }
}
