package org.example;

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;

public class ConcurrentHashMap<K, V> implements Iterable<Map.Entry<K, V>> {

    private static final int DEFAULT_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;

    private static final class Node<K, V> {
        final int hash;
        final K key;
        final AtomicReference<V> value;
        final Node<K, V> next;

        Node(int hash, K key, V value, Node<K, V> next) {
            this.hash = hash;
            this.key = key;
            this.value = new AtomicReference<>(value);
            this.next = next;
        }
    }

    private volatile AtomicReferenceArray<Node<K, V>> table;
    private final LongAdder count = new LongAdder();
    private final ReentrantReadWriteLock tableLock = new ReentrantReadWriteLock();

    public ConcurrentHashMap() {
        this.table = new AtomicReferenceArray<>(DEFAULT_CAPACITY);
    }

    private static int hash(Object key) {
        int h = key.hashCode();
        return h ^ (h >>> 16);
    }

    private static int bucketIndex(int hash, int length) {
        return hash & (length - 1);
    }

    public V get(K key) {
        int h = hash(key);
        AtomicReferenceArray<Node<K, V>> t = table;
        for (Node<K, V> e = t.get(bucketIndex(h, t.length())); e != null; e = e.next) {
            if (e.hash == h && Objects.equals(e.key, key)) return e.value.get();
        }
        return null;
    }

    public V put(K key, V value) {
        int h = hash(key);
        Lock readLock = tableLock.readLock();
        readLock.lock();
        try {
            AtomicReferenceArray<Node<K, V>> t = table;
            int idx = bucketIndex(h, t.length());
            for (;;) {
                Node<K, V> head = t.get(idx);
                for (Node<K, V> e = head; e != null; e = e.next) {
                    if (e.hash == h && Objects.equals(e.key, key)) {
                        V prev;
                        do {
                            prev = e.value.get();
                        } while (!e.value.compareAndSet(prev, value));
                        return prev;
                    }
                }
                if (t.compareAndSet(idx, head, new Node<>(h, key, value, head))) {
                    count.increment();
                    return null;
                }
            }
        } finally {
            readLock.unlock();
            maybeResize();
        }
    }

    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> merger) {
        int h = hash(key);
        Lock readLock = tableLock.readLock();
        readLock.lock();
        try {
            AtomicReferenceArray<Node<K, V>> t = table;
            int idx = bucketIndex(h, t.length());
            for (;;) {
                Node<K, V> head = t.get(idx);
                for (Node<K, V> e = head; e != null; e = e.next) {
                    if (e.hash == h && Objects.equals(e.key, key)) {
                        V prev, merged;
                        do {
                            prev = e.value.get();
                            merged = merger.apply(prev, value);
                        } while (!e.value.compareAndSet(prev, merged));
                        return merged;
                    }
                }
                if (t.compareAndSet(idx, head, new Node<>(h, key, value, head))) {
                    count.increment();
                    return value;
                }
            }
        } finally {
            readLock.unlock();
            maybeResize();
        }
    }

    public int size() {
        return (int) count.sum();
    }

    public void clear() {
        Lock writeLock = tableLock.writeLock();
        writeLock.lock();
        try {
            table = new AtomicReferenceArray<>(DEFAULT_CAPACITY);
            count.reset();
        } finally {
            writeLock.unlock();
        }
    }

    private void maybeResize() {
        if (count.sum() <= (long) (table.length() * LOAD_FACTOR)) return;
        Lock writeLock = tableLock.writeLock();
        writeLock.lock();
        try {
            AtomicReferenceArray<Node<K, V>> oldT = table;
            if (count.sum() <= (long) (oldT.length() * LOAD_FACTOR)) return;
            int newCap = oldT.length() << 1;
            int mask = newCap - 1;
            AtomicReferenceArray<Node<K, V>> newT = new AtomicReferenceArray<>(newCap);
            for (int i = 0; i < oldT.length(); i++) {
                for (Node<K, V> e = oldT.get(i); e != null; e = e.next) {
                    int idx = e.hash & mask;
                    newT.set(idx, new Node<>(e.hash, e.key, e.value.get(), newT.get(idx)));
                }
            }
            table = newT;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Iterator<Map.Entry<K, V>> iterator() {
        return new EntryIterator<>(table);
    }

    private static final class EntryIterator<K, V> implements Iterator<Map.Entry<K, V>> {
        final AtomicReferenceArray<Node<K, V>> t;
        int idx;
        Node<K, V> cur;

        EntryIterator(AtomicReferenceArray<Node<K, V>> t) {
            this.t = t;
            advance();
        }

        private void advance() {
            while (cur == null && idx < t.length()) {
                cur = t.get(idx++);
            }
        }

        @Override
        public boolean hasNext() {
            return cur != null;
        }

        @Override
        public Map.Entry<K, V> next() {
            if (cur == null) throw new NoSuchElementException();
            Node<K, V> e = cur;
            cur = e.next;
            if (cur == null) advance();
            return new AbstractMap.SimpleImmutableEntry<>(e.key, e.value.get());
        }
    }
}
