package org.example.performance;

import org.example.ConcurrentHashMap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiFunction;

public interface MapAdapter<K, V> {
    V get(K key);
    V put(K key, V value);
    V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> merger);
    Iterator<Map.Entry<K, V>> iterator();

    static MapAdapter<Integer, Integer> create(String impl) {
        if (impl.equals("SIMPLE")) return new SimpleAdapter();
        if (impl.equals("CONCURRENT")) return new ConcurrentAdapter();
        return new JdkAdapter();
    }

    final class SimpleAdapter implements MapAdapter<Integer, Integer> {
        private final HashMap<Integer, Integer> m = new HashMap<>();
        public Integer get(Integer k) { return m.get(k); }
        public Integer put(Integer k, Integer v) { return m.put(k, v); }
        public Integer merge(Integer k, Integer v, BiFunction<? super Integer, ? super Integer, ? extends Integer> f) {
            return m.merge(k, v, f);
        }
        public Iterator<Map.Entry<Integer, Integer>> iterator() { return m.entrySet().iterator(); }
    }

    final class ConcurrentAdapter implements MapAdapter<Integer, Integer> {
        private final ConcurrentHashMap<Integer, Integer> m = new ConcurrentHashMap<>();
        public Integer get(Integer k) { return m.get(k); }
        public Integer put(Integer k, Integer v) { return m.put(k, v); }
        public Integer merge(Integer k, Integer v, BiFunction<? super Integer, ? super Integer, ? extends Integer> f) {
            return m.merge(k, v, f);
        }
        public Iterator<Map.Entry<Integer, Integer>> iterator() { return m.iterator(); }
    }

    final class JdkAdapter implements MapAdapter<Integer, Integer> {
        private final java.util.concurrent.ConcurrentHashMap<Integer, Integer> m = new java.util.concurrent.ConcurrentHashMap<>();
        public Integer get(Integer k) { return m.get(k); }
        public Integer put(Integer k, Integer v) { return m.put(k, v); }
        public Integer merge(Integer k, Integer v, BiFunction<? super Integer, ? super Integer, ? extends Integer> f) {
            return m.merge(k, v, f);
        }
        public Iterator<Map.Entry<Integer, Integer>> iterator() { return m.entrySet().iterator(); }
    }
}
