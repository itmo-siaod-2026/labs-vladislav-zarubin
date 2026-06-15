package org.example;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws Exception {
        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

        map.put("a", 1);
        map.put("b", 2);
        map.put("c", 3);
        System.out.println("get(a) = " + map.get("a"));
        System.out.println("get(missing) = " + map.get("missing"));
        System.out.println("put(a, 10) old = " + map.put("a", 10));
        System.out.println("size = " + map.size());

        System.out.println("\niterator:");
        for (Map.Entry<String, Integer> e : map) {
            System.out.println(e.getKey() + " -> " + e.getValue());
        }

        ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
        for (String w : new String[]{"foo", "bar", "foo", "baz", "bar", "foo"}) {
            counts.merge(w, 1, Integer::sum);
        }
        System.out.println("\nword counts: ");
        for (Map.Entry<String, Integer> e : counts) {
            System.out.println(e.getKey() + "=" + e.getValue());
        }

        ConcurrentHashMap<Integer, Integer> shared = new ConcurrentHashMap<>();
        int threads = 8, iters = 100_000, keys = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < iters; i++) shared.merge(i % keys, 1, Integer::sum);
                return null;
            });
        }
        long t0 = System.nanoTime();
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);
        long ms = (System.nanoTime() - t0) / 1_000_000;

        long total = 0;
        for (int k = 0; k < keys; k++) total += shared.get(k);
        System.out.println("\nconcurrent merge: " + threads + " threads x " + iters + " incs, " + keys + " keys");
        System.out.println("expected " + ((long) threads * iters) + ", got " + total + " (" + ms + " ms)");

        shared.clear();
        System.out.println("after clear: size=" + shared.size() + ", get(0)=" + shared.get(0));
    }
}
