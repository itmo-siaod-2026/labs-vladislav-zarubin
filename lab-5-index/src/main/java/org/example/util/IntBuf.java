package org.example.util;

import java.util.Arrays;

public final class IntBuf {
    public int[] a = new int[16];
    public int size = 0;

    public void add(int x) {
        if (size == a.length) {
            a = Arrays.copyOf(a, a.length * 2);
        }
        a[size++] = x;
    }

    public int last() {
        return a[size - 1];
    }

    public void incLast() {
        a[size - 1]++;
    }

    public int[] trimmed() {
        return Arrays.copyOf(a, size);
    }
}
