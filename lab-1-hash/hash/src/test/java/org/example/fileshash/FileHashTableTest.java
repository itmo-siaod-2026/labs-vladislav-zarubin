package org.example.fileshash;

import org.example.support.RandomDataFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileHashTableTest {
    @TempDir
    Path tempDir;

    @Test
    void reopenKeepsData() throws IOException {
        Path directory = tempDir.resolve("table");

        FileHashTable table = new FileHashTable(directory, 4);
        table.insert("user:1", "alice");
        table.insert("user:2", "bob");
        table.update("user:2", "bobby");
        table.delete("user:1");
        table.close();

        FileHashTable reopened = new FileHashTable(directory, 4);
        assertNull(reopened.get("user:1"));
        assertEquals("bobby", reopened.get("user:2"));
        assertEquals(1, reopened.size());
        reopened.close();
    }

    @Test
    void randomOps() throws IOException {
        Path directory = tempDir.resolve("random-table");
        List<String> keys = RandomDataFactory.uniqueKeys(300, 11L);
        Random random = new Random(17L);
        Map<String, String> expected = new HashMap<>();

        FileHashTable table = new FileHashTable(directory, 8);
        for (int step = 0; step < 5_000; step++) {
            String key = keys.get(random.nextInt(keys.size()));
            String value = "v" + step;
            int operation = random.nextInt(4);

            switch (operation) {
                case 0 -> {
                    if (expected.containsKey(key)) {
                        assertThrows(IllegalArgumentException.class, () -> table.insert(key, value));
                    } else {
                        table.insert(key, value);
                        expected.put(key, value);
                    }
                }
                case 1 -> {
                    if (expected.containsKey(key)) {
                        table.update(key, value);
                        expected.put(key, value);
                    } else {
                        assertThrows(IllegalArgumentException.class, () -> table.update(key, value));
                    }
                }
                case 2 -> {
                    boolean removed = table.delete(key);
                    boolean expectedRemoved = expected.remove(key) != null;
                    assertEquals(expectedRemoved, removed);
                }
                case 3 -> assertEquals(expected.get(key), table.get(key));
                default -> throw new IllegalStateException("Unexpected random operation");
            }
        }

        assertEquals(expected.size(), table.size());
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            assertEquals(entry.getValue(), table.get(entry.getKey()));
            assertTrue(table.containsKey(entry.getKey()));
        }

        List<String> missing = RandomDataFactory.missingKeys(30, 33L);
        for (String key : missing) {
            assertFalse(table.containsKey(key));
        }

        table.close();
    }

    @Test
    void bucketSplit() throws IOException {
        Path directory = tempDir.resolve("extendible-table");
        FileHashTable table = new FileHashTable(directory, 2);

        table.insert("k1", "v1");
        table.insert("k2", "v2");
        table.insert("k3", "v3");
        table.insert("k4", "v4");

        assertTrue(table.globalDepth() > 0);
        assertTrue(table.bucketCount() > 1);
        table.close();
    }

    @Test
    void manyBucketSplitsRemainUsable() throws IOException {
        Path directory = tempDir.resolve("many-splits");
        FileHashTable table = new FileHashTable(directory, 1);

        for (int index = 0; index < 600; index++) {
            table.insert("key-" + index, "value-" + index);
        }

        assertEquals(600, table.size());
        assertEquals("value-0", table.get("key-0"));
        assertEquals("value-299", table.get("key-299"));
        assertEquals("value-599", table.get("key-599"));
        table.close();
    }

    @Test
    void collidingKeysFailFastWhenSplitCannotHelp() throws IOException {
        Path directory = tempDir.resolve("colliding-keys");
        FileHashTable table = new FileHashTable(directory, 1);

        table.insert("Aa", "first");

        assertThrows(IllegalStateException.class, () -> table.insert("BB", "second"));
        assertEquals(1, table.size());
        assertEquals("first", table.get("Aa"));
        assertNull(table.get("BB"));
        table.close();
    }

}
