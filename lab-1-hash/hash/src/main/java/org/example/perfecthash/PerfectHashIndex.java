package org.example.perfecthash;

import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

public final class PerfectHashIndex {
    private static final long DEFAULT_SEED = 200000000L;
    private static final int MAX_PRIMARY_ATTEMPTS = 10_000;
    private static final int MAX_SECONDARY_ATTEMPTS = 10_000;
    private static final long HASH_MAX_VALUE = (1L << 61) - 1;
    private static final long KEY_BASE = 257L;
    private final long primaryMultiplier;
    private final long primaryShift;
    private final int primaryTableSize;
    private final int[] bucketBase;
    private final int[] bucketSize;
    private final long[] bucketMultiplier;
    private final long[] bucketShift;
    private final int[] slotEntryIndex;
    private final char[][] storedKeys;
    private final long[] storedNumericKeys;

    private final int size;

    private PerfectHashIndex(
            long primaryMultiplier,
            long primaryShift,
            int primaryTableSize,
            int[] bucketBase,
            int[] bucketSize,
            long[] bucketMultiplier,
            long[] bucketShift,
            int[] slotEntryIndex,
            char[][] storedKeys,
            long[] storedNumericKeys,
            int size
    ) {
        this.primaryMultiplier = primaryMultiplier;
        this.primaryShift = primaryShift;
        this.primaryTableSize = primaryTableSize;
        this.bucketBase = bucketBase;
        this.bucketSize = bucketSize;
        this.bucketMultiplier = bucketMultiplier;
        this.bucketShift = bucketShift;
        this.slotEntryIndex = slotEntryIndex;
        this.storedKeys = storedKeys;
        this.storedNumericKeys = storedNumericKeys;
        this.size = size;
    }

    public static PerfectHashIndex fromKeys(Collection<String> keys) {
        return fromKeys(keys, DEFAULT_SEED);
    }

    public static PerfectHashIndex fromKeys(Collection<String> keys, long seed) {
        int keyCount = keys.size();

        if (keyCount == 0) {
            return new PerfectHashIndex(
                    1L, 0L, 1,
                    new int[0], new int[0],
                    new long[0], new long[0],
                    new int[0], new char[0][], new long[0],
                    0
            );
        }

        char[][] storedKeys = new char[keyCount][];
        long[] storedNumericKeys = new long[keyCount];
        int index = 0;
        for (String key : keys) {
            char[] chars = key.toCharArray();
            storedKeys[index] = chars;
            storedNumericKeys[index] = numericKey(chars);
            index++;
        }

        rejectDuplicates(storedKeys, storedNumericKeys);

        int tableSize = keyCount;
        Random random = new Random(seed);
        int[] bucketSize = new int[tableSize];
        int[] entryBucket = new int[keyCount];

        long selectedPrimaryMultiplier = 0L;
        long selectedPrimaryShift = 0L;
        boolean primarySelected = false;

        for (int attempt = 0; attempt < MAX_PRIMARY_ATTEMPTS; attempt++) {
            long candidateMultiplier = randomMultiplier(random);
            long candidateShift = randomShift(random);

            Arrays.fill(bucketSize, 0);
            for (int entryIndex = 0; entryIndex < keyCount; entryIndex++) {
                int bucket = universalApply(
                        candidateMultiplier,
                        candidateShift,
                        storedNumericKeys[entryIndex],
                        tableSize
                );
                entryBucket[entryIndex] = bucket;
                bucketSize[bucket]++;
            }

            long squaredBucketSum = 0L;
            for (int bucket = 0; bucket < tableSize; bucket++) {
                long count = bucketSize[bucket];
                squaredBucketSum += count * count;
            }

            if (squaredBucketSum <= 4L * tableSize) {
                selectedPrimaryMultiplier = candidateMultiplier;
                selectedPrimaryShift = candidateShift;
                primarySelected = true;
                break;
            }
        }

        if (!primarySelected) {
            throw new IllegalStateException("Could not build primary hash without too many collisions");
        }

        int[] bucketBase = new int[tableSize];
        int totalSlots = 0;
        for (int bucket = 0; bucket < tableSize; bucket++) {
            int count = bucketSize[bucket];
            bucketBase[bucket] = totalSlots;
            totalSlots += secondarySlotCount(count);
        }

        int[] bucketEntryOffset = new int[tableSize];
        int running = 0;
        for (int bucket = 0; bucket < tableSize; bucket++) {
            bucketEntryOffset[bucket] = running;
            running += bucketSize[bucket];
        }

        int[] groupedEntries = new int[keyCount];
        int[] bucketCursor = new int[tableSize];
        for (int entryIndex = 0; entryIndex < keyCount; entryIndex++) {
            int bucket = entryBucket[entryIndex];
            groupedEntries[bucketEntryOffset[bucket] + bucketCursor[bucket]] = entryIndex;
            bucketCursor[bucket]++;
        }

        long[] bucketMultiplier = new long[tableSize];
        long[] bucketShift = new long[tableSize];
        int[] slotEntryIndex = new int[totalSlots];
        Arrays.fill(slotEntryIndex, -1);

        for (int bucket = 0; bucket < tableSize; bucket++) {
            int count = bucketSize[bucket];
            if (count == 0) {
                continue;
            }

            int entryOffset = bucketEntryOffset[bucket];
            int base = bucketBase[bucket];
            int slotCount = secondarySlotCount(count);

            if (count == 1) {
                bucketMultiplier[bucket] = 1L;
                bucketShift[bucket] = 0L;
                slotEntryIndex[base] = groupedEntries[entryOffset];
                continue;
            }

            boolean secondarySelected = false;
            for (int attempt = 0; attempt < MAX_SECONDARY_ATTEMPTS; attempt++) {
                long candidateMultiplier = randomMultiplier(random);
                long candidateShift = randomShift(random);

                for (int slot = 0; slot < slotCount; slot++) {
                    slotEntryIndex[base + slot] = -1;
                }

                boolean collision = false;
                for (int offset = 0; offset < count; offset++) {
                    int entryIndex = groupedEntries[entryOffset + offset];
                    int slot = universalApply(
                            candidateMultiplier,
                            candidateShift,
                            storedNumericKeys[entryIndex],
                            slotCount
                    );
                    if (slotEntryIndex[base + slot] != -1) {
                        collision = true;
                        break;
                    }
                    slotEntryIndex[base + slot] = entryIndex;
                }

                if (!collision) {
                    bucketMultiplier[bucket] = candidateMultiplier;
                    bucketShift[bucket] = candidateShift;
                    secondarySelected = true;
                    break;
                }
            }

            if (!secondarySelected) {
                throw new IllegalStateException("Could not build a collision-free secondary table");
            }
        }

        return new PerfectHashIndex(
                selectedPrimaryMultiplier,
                selectedPrimaryShift,
                tableSize,
                bucketBase,
                bucketSize,
                bucketMultiplier,
                bucketShift,
                slotEntryIndex,
                storedKeys,
                storedNumericKeys,
                keyCount
        );
    }

    public int find(String key) {
        if (size == 0) {
            return -1;
        }

        long numericKey = numericKey(key);
        int bucket = universalApply(primaryMultiplier, primaryShift, numericKey, primaryTableSize);
        int count = bucketSize[bucket];
        if (count == 0) {
            return -1;
        }

        int slot;
        if (count == 1) {
            slot = 0;
        } else {
            slot = universalApply(
                    bucketMultiplier[bucket],
                    bucketShift[bucket],
                    numericKey,
                    count * count
            );
        }

        int entryIndex = slotEntryIndex[bucketBase[bucket] + slot];
        if (entryIndex < 0) {
            return -1;
        }

        if (storedNumericKeys[entryIndex] != numericKey) {
            return -1;
        }

        if (!sameChars(storedKeys[entryIndex], key)) {
            return -1;
        }

        return entryIndex;
    }

    public boolean contains(String key) {
        return find(key) >= 0;
    }

    public int size() {
        return size;
    }

    private static void rejectDuplicates(char[][] storedKeys, long[] storedNumericKeys) {
        int keyCount = storedKeys.length;
        int capacity = Integer.highestOneBit(Math.max(16, keyCount * 2 - 1)) << 1;
        int mask = capacity - 1;
        int[] table = new int[capacity];
        Arrays.fill(table, -1);

        for (int entryIndex = 0; entryIndex < keyCount; entryIndex++) {
            long numericKey = storedNumericKeys[entryIndex];
            int slot = ((int) numericKey) & mask;

            while (table[slot] != -1) {
                int other = table[slot];
                if (storedNumericKeys[other] == numericKey
                        && Arrays.equals(storedKeys[other], storedKeys[entryIndex])) {
                    throw new IllegalArgumentException("duplicate in perfect hash");
                }
                slot = (slot + 1) & mask;
            }
            table[slot] = entryIndex;
        }
    }

    private static long numericKey(char[] chars) {
        long value = 0L;
        for (int index = 0; index < chars.length; index++) {
            value = addMod(mulMod(value, KEY_BASE), chars[index] + 1L);
        }
        return value;
    }

    private static long numericKey(String key) {
        long value = 0L;
        int length = key.length();
        for (int index = 0; index < length; index++) {
            value = addMod(mulMod(value, KEY_BASE), key.charAt(index) + 1L);
        }
        return value;
    }

    private static boolean sameChars(char[] stored, String key) {
        if (stored.length != key.length()) {
            return false;
        }
        for (int index = 0; index < stored.length; index++) {
            if (stored[index] != key.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static long addMod(long left, long right) {
        long sum = left + right;
        if (sum >= HASH_MAX_VALUE) {
            sum -= HASH_MAX_VALUE;
        }
        return sum;
    }

    private static long mulMod(long left, long right) {
        long low = left * right;
        long high = Math.multiplyHigh(left, right);
        long reduced = (low & HASH_MAX_VALUE) + (low >>> 61) + (high << 3);
        reduced = (reduced & HASH_MAX_VALUE) + (reduced >>> 61);
        if (reduced >= HASH_MAX_VALUE) {
            reduced -= HASH_MAX_VALUE;
        }
        return reduced;
    }

    private static int universalApply(long multiplier, long shift, long numericKey, int tableSize) {
        if (tableSize <= 1) {
            return 0;
        }
        long hashValue = addMod(mulMod(multiplier, numericKey), shift);
        return (int) (hashValue % tableSize);
    }

    private static int secondarySlotCount(int count) {
        if (count == 0) {
            return 0;
        }
        if (count == 1) {
            return 1;
        }
        return count * count;
    }

    private static long randomMultiplier(Random random) {
        return 1L + Math.floorMod(random.nextLong(), HASH_MAX_VALUE - 1L);
    }

    private static long randomShift(Random random) {
        return Math.floorMod(random.nextLong(), HASH_MAX_VALUE);
    }
}
