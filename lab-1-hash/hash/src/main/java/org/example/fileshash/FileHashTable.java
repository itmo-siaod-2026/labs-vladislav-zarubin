package org.example.fileshash;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FileHashTable {
    private static final int MAX_SPLIT_ATTEMPTS_PER_INSERT = 20;
    private static final String META_FILE_NAME = "directory.bin";
    private static final String BUCKET_FILE_PATTERN = "bucket-%05d.bin";
    private static final int MAX_KEY_BYTES = 32;
    private static final int MAX_VALUE_BYTES = 64;
    private static final int HEADER_LOCAL_DEPTH = 0;
    private static final int HEADER_ENTRY_COUNT = 4;
    private static final int HEADER_SIZE = 8;
    private static final int SLOT_STATE = 0;
    private static final int SLOT_HASH = 4;
    private static final int SLOT_KEY = 8;
    private static final int SLOT_VALUE = SLOT_KEY + MAX_KEY_BYTES;
    private static final int SLOT_SIZE = SLOT_VALUE + MAX_VALUE_BYTES;
    private static final int SLOT_OCCUPIED = 1;
    private final Path rootDirectory;
    private final int bucketCapacity;
    private final int bucketFileSize;
    private final Map<Integer, BucketHandle> bucketHandles = new HashMap<>();
    private int globalDepth;
    private int nextBucketId;
    private long size;
    private int[] directory;

    public FileHashTable(Path rootDirectory, int bucketCapacity) throws IOException {
        this.rootDirectory = rootDirectory;
        this.bucketCapacity = bucketCapacity;
        this.bucketFileSize = HEADER_SIZE + bucketCapacity * SLOT_SIZE;

        Files.createDirectories(rootDirectory);

        if (Files.exists(metaFile())) {
            loadMetadata();
            size = loadSizeFromBuckets();
        } else {
            initializeEmptyTable();
        }
    }

    public void insert(String key, String value) throws IOException {
        byte[] keyBytes = keyBytes(key);
        byte[] valueBytes = valueBytes(value);
        int hash = hash(key);
        int splitAttempts = 0;

        while (true) {
            int bucketId = directory[directoryIndex(hash)];
            BucketHandle bucket = bucketHandle(bucketId);
            Scan scan = scanBucket(bucket.buffer, hash, keyBytes);

            if (scan.matchSlot >= 0) {
                throw new IllegalArgumentException("key already exists: " + key);
            }

            if (scan.entryCount < bucketCapacity) {
                writeSlot(bucket.buffer, scan.firstFreeSlot, hash, keyBytes, valueBytes);
                bucket.buffer.putInt(HEADER_ENTRY_COUNT, scan.entryCount + 1);
                bucket.dirty = true;
                size++;
                return;
            }

            if (splitAttempts++ >= MAX_SPLIT_ATTEMPTS_PER_INSERT || !splitBucket(bucketId)) {
                throw new IllegalStateException("Bucket split did not free space for key: " + key);
            }
        }
    }

    public void update(String key, String value) throws IOException {
        byte[] keyBytes = keyBytes(key);
        byte[] valueBytes = valueBytes(value);
        int hash = hash(key);
        int bucketId = directory[directoryIndex(hash)];
        BucketHandle bucket = bucketHandle(bucketId);
        Scan scan = scanBucket(bucket.buffer, hash, keyBytes);

        if (scan.matchSlot < 0) {
            throw new IllegalArgumentException("Key does not exist: " + key);
        }

        writeValue(bucket.buffer, scan.matchSlot, valueBytes);
        bucket.dirty = true;
    }

    public boolean delete(String key) throws IOException {
        byte[] keyBytes = keyBytes(key);
        int hash = hash(key);
        int bucketId = directory[directoryIndex(hash)];
        BucketHandle bucket = bucketHandle(bucketId);
        Scan scan = scanBucket(bucket.buffer, hash, keyBytes);

        if (scan.matchSlot < 0) {
            return false;
        }

        clearSlot(bucket.buffer, scan.matchSlot);
        bucket.buffer.putInt(HEADER_ENTRY_COUNT, scan.entryCount - 1);
        bucket.dirty = true;
        size--;
        return true;
    }

    public String get(String key) throws IOException {
        byte[] keyBytes = keyBytes(key);
        int hash = hash(key);
        int bucketId = directory[directoryIndex(hash)];
        BucketHandle bucket = bucketHandle(bucketId);
        Scan scan = scanBucket(bucket.buffer, hash, keyBytes);

        if (scan.matchSlot < 0) {
            return null;
        }

        return readFixedString(bucket.buffer, slotPosition(scan.matchSlot) + SLOT_VALUE, MAX_VALUE_BYTES);
    }

    public boolean containsKey(String key) throws IOException {
        return get(key) != null;
    }

    public long size() {
        return size;
    }

    public int bucketCount() {
        Set<Integer> uniqueBucketIds = new HashSet<>();
        for (int bucketId : directory) {
            uniqueBucketIds.add(bucketId);
        }
        return uniqueBucketIds.size();
    }

    public int globalDepth() {
        return globalDepth;
    }

    private void initializeEmptyTable() throws IOException {
        globalDepth = 0;
        nextBucketId = 1;
        size = 0;
        directory = new int[]{0};

        rewriteBucket(0, 0, new Entry[0], 0);
        saveMetadata();
    }

    private void loadMetadata() throws IOException {
        Path metadataFile = metaFile();
        long fileSize = Files.size(metadataFile);

        try (FileChannel channel = FileChannel.open(metadataFile, StandardOpenOption.READ)) {
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);

            buffer.getInt();
            int storedGlobalDepth = buffer.getInt();
            int storedNextBucketId = buffer.getInt();
            int directoryLength = buffer.getInt();

            directory = new int[directoryLength];
            for (int index = 0; index < directoryLength; index++) {
                directory[index] = buffer.getInt();
            }

            globalDepth = storedGlobalDepth;
            nextBucketId = storedNextBucketId;
        }
    }

    private void saveMetadata() throws IOException {
        int bytes = Integer.BYTES * 4 + directory.length * Integer.BYTES;

        try (FileChannel channel = FileChannel.open(
                metaFile(),
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
        )) {
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, bytes);
            buffer.putInt(bucketCapacity);
            buffer.putInt(globalDepth);
            buffer.putInt(nextBucketId);
            buffer.putInt(directory.length);
            for (int bucketId : directory) {
                buffer.putInt(bucketId);
            }
            buffer.force();
        }
    }

    private long loadSizeFromBuckets() throws IOException {
        long total = 0;
        Set<Integer> uniqueBucketIds = new HashSet<>();

        for (int bucketId : directory) {
            if (!uniqueBucketIds.add(bucketId)) {
                continue;
            }
            total += bucketHandle(bucketId).buffer.getInt(HEADER_ENTRY_COUNT);
        }

        return total;
    }

    private boolean splitBucket(int bucketId) throws IOException {
        BucketHandle bucket = bucketHandle(bucketId);
        int localDepth = bucket.buffer.getInt(HEADER_LOCAL_DEPTH);
        Entry[] entries = readEntries(bucket.buffer, bucket.buffer.getInt(HEADER_ENTRY_COUNT));

        if (localDepth >= 31) {
            return false;
        }

        if (localDepth == globalDepth) {
            doubleDirectory();
        }

        int splitBit = 1 << localDepth;
        int newLocalDepth = localDepth + 1;
        int newBucketId = nextBucketId++;

        Entry[] left = new Entry[entries.length];
        Entry[] right = new Entry[entries.length];
        int leftCount = 0;
        int rightCount = 0;

        for (Entry entry : entries) {
            if ((entry.hash & splitBit) == 0) {
                left[leftCount++] = entry;
            } else {
                right[rightCount++] = entry;
            }
        }

        rewriteBucket(bucketId, newLocalDepth, left, leftCount);
        rewriteBucket(newBucketId, newLocalDepth, right, rightCount);

        for (int index = 0; index < directory.length; index++) {
            if (directory[index] == bucketId && (index & splitBit) != 0) {
                directory[index] = newBucketId;
            }
        }

        saveMetadata();
        return true;
    }

    private void rewriteBucket(int bucketId, int localDepth, Entry[] entries, int entryCount) throws IOException {
        BucketHandle bucket = bucketHandle(bucketId);
        clearBucket(bucket.buffer);
        bucket.buffer.putInt(HEADER_LOCAL_DEPTH, localDepth);
        bucket.buffer.putInt(HEADER_ENTRY_COUNT, entryCount);

        for (int index = 0; index < entryCount; index++) {
            Entry entry = entries[index];
            writeSlot(bucket.buffer, index, entry.hash, entry.keyBytes, entry.valueBytes);
        }

        bucket.dirty = true;
    }

    private Entry[] readEntries(MappedByteBuffer buffer, int entryCount) {
        Entry[] entries = new Entry[entryCount];
        int stored = 0;

        for (int slot = 0; slot < bucketCapacity && stored < entryCount; slot++) {
            int slotPosition = slotPosition(slot);
            if (buffer.getInt(slotPosition + SLOT_STATE) != SLOT_OCCUPIED) {
                continue;
            }

            entries[stored++] = new Entry(
                    buffer.getInt(slotPosition + SLOT_HASH),
                    readFixedBytes(buffer, slotPosition + SLOT_KEY, MAX_KEY_BYTES),
                    readFixedBytes(buffer, slotPosition + SLOT_VALUE, MAX_VALUE_BYTES)
            );
        }

        return entries;
    }

    private Scan scanBucket(MappedByteBuffer buffer, int hash, byte[] keyBytes) {
        Scan scan = new Scan();
        scan.localDepth = buffer.getInt(HEADER_LOCAL_DEPTH);
        scan.entryCount = buffer.getInt(HEADER_ENTRY_COUNT);
        scan.matchSlot = -1;
        scan.firstFreeSlot = -1;

        for (int slot = 0; slot < bucketCapacity; slot++) {
            int slotPosition = slotPosition(slot);
            int state = buffer.getInt(slotPosition + SLOT_STATE);
            if (state != SLOT_OCCUPIED) {
                if (scan.firstFreeSlot < 0) {
                    scan.firstFreeSlot = slot;
                }
                continue;
            }

            if (scan.matchSlot >= 0) {
                continue;
            }

            if (buffer.getInt(slotPosition + SLOT_HASH) != hash) {
                continue;
            }

            if (matchesFixedBytes(buffer, slotPosition + SLOT_KEY, MAX_KEY_BYTES, keyBytes)) {
                scan.matchSlot = slot;
            }
        }

        return scan;
    }

    private void doubleDirectory() {
        int[] expanded = new int[directory.length * 2];
        for (int index = 0; index < directory.length; index++) {
            expanded[index] = directory[index];
            expanded[index + directory.length] = directory[index];
        }
        directory = expanded;
        globalDepth++;
    }

    private int directoryIndex(int hash) {
        if (globalDepth == 0) {
            return 0;
        }
        return hash & ((1 << globalDepth) - 1);
    }

    private int hash(String key) {
        int hash = key.hashCode();
        return hash ^ (hash >>> 16);
    }

    private byte[] keyBytes(String key) {
        return toFixedBytes(key, MAX_KEY_BYTES);
    }

    private byte[] valueBytes(String value) {
        return toFixedBytes(value, MAX_VALUE_BYTES);
    }

    private byte[] toFixedBytes(String value, int maxBytes) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return bytes;
        }

        byte[] truncated = new byte[maxBytes];
        System.arraycopy(bytes, 0, truncated, 0, maxBytes);
        return truncated;
    }

    private int slotPosition(int slotIndex) {
        return HEADER_SIZE + slotIndex * SLOT_SIZE;
    }

    private Path metaFile() {
        return rootDirectory.resolve(META_FILE_NAME);
    }

    private Path bucketFile(int bucketId) {
        return rootDirectory.resolve(BUCKET_FILE_PATTERN.formatted(bucketId));
    }

    private BucketHandle bucketHandle(int bucketId) throws IOException {
        BucketHandle existing = bucketHandles.get(bucketId);
        if (existing != null) {
            return existing;
        }

        BucketHandle handle = new BucketHandle();
        try (FileChannel channel = FileChannel.open(
                bucketFile(bucketId),
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE
        )) {
            if (channel.size() != bucketFileSize) {
                channel.truncate(bucketFileSize);
            }
            handle.buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, bucketFileSize);
        }
        bucketHandles.put(bucketId, handle);
        return handle;
    }

    private void clearBucket(MappedByteBuffer buffer) {
        for (int index = 0; index < bucketFileSize; index++) {
            buffer.put(index, (byte) 0);
        }
    }

    private void clearSlot(MappedByteBuffer buffer, int slotIndex) {
        int slotPosition = slotPosition(slotIndex);
        for (int index = 0; index < SLOT_SIZE; index++) {
            buffer.put(slotPosition + index, (byte) 0);
        }
    }

    private void writeSlot(MappedByteBuffer buffer, int slotIndex, int hash, byte[] keyBytes, byte[] valueBytes) {
        int slotPosition = slotPosition(slotIndex);
        buffer.putInt(slotPosition + SLOT_STATE, SLOT_OCCUPIED);
        buffer.putInt(slotPosition + SLOT_HASH, hash);
        writeFixedBytes(buffer, slotPosition + SLOT_KEY, MAX_KEY_BYTES, keyBytes);
        writeFixedBytes(buffer, slotPosition + SLOT_VALUE, MAX_VALUE_BYTES, valueBytes);
    }

    private void writeValue(MappedByteBuffer buffer, int slotIndex, byte[] valueBytes) {
        int slotPosition = slotPosition(slotIndex);
        writeFixedBytes(buffer, slotPosition + SLOT_VALUE, MAX_VALUE_BYTES, valueBytes);
    }

    private void writeFixedBytes(MappedByteBuffer buffer, int offset, int fieldSize, byte[] bytes) {
        for (int index = 0; index < fieldSize; index++) {
            byte value = index < bytes.length ? bytes[index] : 0;
            buffer.put(offset + index, value);
        }
    }

    private byte[] readFixedBytes(MappedByteBuffer buffer, int offset, int fieldSize) {
        int length = 0;
        while (length < fieldSize && buffer.get(offset + length) != 0) {
            length++;
        }

        byte[] bytes = new byte[length];
        for (int index = 0; index < length; index++) {
            bytes[index] = buffer.get(offset + index);
        }
        return bytes;
    }

    private String readFixedString(MappedByteBuffer buffer, int offset, int fieldSize) {
        return new String(readFixedBytes(buffer, offset, fieldSize), StandardCharsets.UTF_8);
    }

    private boolean matchesFixedBytes(MappedByteBuffer buffer, int offset, int fieldSize, byte[] expected) {
        for (int index = 0; index < expected.length; index++) {
            if (buffer.get(offset + index) != expected[index]) {
                return false;
            }
        }
        for (int index = expected.length; index < fieldSize; index++) {
            if (buffer.get(offset + index) != 0) {
                return false;
            }
        }
        return true;
    }

    public void close() throws IOException {
        for (BucketHandle bucket : bucketHandles.values()) {
            if (bucket.dirty) {
                bucket.buffer.force();
            }
        }
        bucketHandles.clear();
    }

    private record Entry(int hash, byte[] keyBytes, byte[] valueBytes) {
    }

    private static final class Scan {
        private int localDepth;
        private int entryCount;
        private int matchSlot;
        private int firstFreeSlot;
    }

    private static final class BucketHandle {
        private MappedByteBuffer buffer;
        private boolean dirty;
    }
}
