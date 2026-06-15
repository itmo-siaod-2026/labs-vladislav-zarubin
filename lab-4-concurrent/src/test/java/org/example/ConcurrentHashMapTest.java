package org.example;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentHashMapTest {

    @Test
    void putGetSize() {
        ConcurrentHashMap<Integer, String> m = new ConcurrentHashMap<>();
        assertEquals(0, m.size());
        assertNull(m.put(1, "a"));
        assertEquals(1, m.size());
        assertEquals("a", m.get(1));
        assertEquals("a", m.put(1, "b"));
        assertEquals("b", m.get(1));
        assertEquals(1, m.size());
        assertNull(m.get(42));
    }

    @Test
    void clear() {
        ConcurrentHashMap<Integer, Integer> m = new ConcurrentHashMap<>();
        for (int i = 0; i < 1000; i++) m.put(i, i);
        m.clear();
        assertEquals(0, m.size());
        for (int i = 0; i < 1000; i++) assertNull(m.get(i));
    }

    @Test
    void resizeKeepsAllEntries() {
        ConcurrentHashMap<Integer, Integer> m = new ConcurrentHashMap<>();
        for (int i = 0; i < 100_000; i++) m.put(i, i * 2);
        assertEquals(100_000, m.size());
        for (int i = 0; i < 100_000; i++) assertEquals(i * 2, m.get(i));
    }

    @Test
    void mergeSums() {
        ConcurrentHashMap<Integer, Integer> m = new ConcurrentHashMap<>();
        for (int i = 0; i < 1000; i++) m.merge(i % 10, 1, Integer::sum);
        for (int i = 0; i < 10; i++) assertEquals(100, m.get(i));
    }

    @Test
    void randomMatchesJdk() {
        Random r = new Random(42);
        ConcurrentHashMap<Integer, Integer> mine = new ConcurrentHashMap<>();
        HashMap<Integer, Integer> jdk = new HashMap<>();
        for (int i = 0; i < 50_000; i++) {
            int key = r.nextInt(2000);
            int value = r.nextInt();
            assertEquals(jdk.put(key, value), mine.put(key, value));
        }
        assertEquals(jdk.size(), mine.size());
        for (Map.Entry<Integer, Integer> e : jdk.entrySet()) {
            assertEquals(e.getValue(), mine.get(e.getKey()));
        }
    }

    @Test
    void iteratorYieldsAllEntries() {
        ConcurrentHashMap<Integer, Integer> m = new ConcurrentHashMap<>();
        Set<Integer> expected = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            m.put(i, i);
            expected.add(i);
        }
        Set<Integer> seen = new HashSet<>();
        for (Map.Entry<Integer, Integer> e : m) {
            assertEquals(e.getKey(), e.getValue());
            seen.add(e.getKey());
        }
        assertEquals(expected, seen);
    }

    @Test
    void concurrentDisjointPuts() throws Exception {
        int threads = 8;
        int perThread = 50_000;
        ConcurrentHashMap<Integer, Integer> m = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        for (int t = 0; t < threads; t++) {
            int base = t * perThread;
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < perThread; i++) m.put(base + i, base + i);
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
        assertEquals(threads * perThread, m.size());
        for (int i = 0; i < threads * perThread; i++) assertEquals(i, m.get(i));
    }

    @Test
    void concurrentMergeSums() throws Exception {
        int threads = 8;
        int iters = 100_000;
        int keys = 16;
        ConcurrentHashMap<Integer, Integer> m = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < iters; i++) m.merge(i % keys, 1, Integer::sum);
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
        long total = 0;
        for (int k = 0; k < keys; k++) total += m.get(k);
        assertEquals((long) threads * iters, total);
    }

    @Test
    void concurrentReadDuringWrites() throws Exception {
        int writers = 4;
        int readers = 4;
        int n = 50_000;
        ConcurrentHashMap<Integer, Integer> m = new ConcurrentHashMap<>();
        for (int i = 0; i < n; i++) m.put(i, i);
        ExecutorService pool = Executors.newFixedThreadPool(writers + readers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();
        for (int w = 0; w < writers; w++) {
            int seed = w;
            pool.submit(() -> {
                start.await();
                Random r = new Random(seed);
                for (int i = 0; i < 50_000; i++) m.put(r.nextInt(n), r.nextInt());
                return null;
            });
        }
        for (int rIdx = 0; rIdx < readers; rIdx++) {
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < 200_000; i++) {
                    Integer v = m.get(i % n);
                    if (v == null) failures.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
        assertEquals(0, failures.get(), "readers must always see a value for pre-populated keys");
        for (int i = 0; i < n; i++) assertNotNull(m.get(i));
    }
}
