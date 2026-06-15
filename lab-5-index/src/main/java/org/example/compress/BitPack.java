package org.example.compress;

import java.util.Arrays;

public final class BitPack {
    private BitPack() {
    }

    public static int bitsRequired(int value) {
        return value <= 0 ? 0 : 32 - Integer.numberOfLeadingZeros(value);
    }

    public static int maxBits(int[] values, int off, int len) {
        int max = 0;
        for (int i = 0; i < len; i++) {
            if (values[off + i] > max) {
                max = values[off + i];
            }
        }
        return bitsRequired(max);
    }

    public static int packedLength(int count, int bits) {
        return (int) (((long) count * bits + 7) / 8);
    }

    public static byte[] pack(int[] values, int off, int len, int bits) {
        if (bits == 0) {
            return new byte[0];
        }
        byte[] dst = new byte[packedLength(len, bits)];
        long mask = bits >= 32 ? 0xFFFFFFFFL : (1L << bits) - 1;
        long acc = 0;
        int bitCount = 0;
        int dp = 0;
        for (int i = 0; i < len; i++) {
            acc |= ((long) values[off + i] & mask) << bitCount;
            bitCount += bits;
            while (bitCount >= 8) {
                dst[dp++] = (byte) acc;
                acc >>>= 8;
                bitCount -= 8;
            }
        }
        if (bitCount > 0) {
            dst[dp] = (byte) acc;
        }
        return dst;
    }

    public static void unpack(byte[] src, int srcOff, int bits, int len, int[] out) {
        if (bits == 0) {
            Arrays.fill(out, 0, len, 0);
            return;
        }
        long mask = bits >= 32 ? 0xFFFFFFFFL : (1L << bits) - 1;
        long acc = 0;
        int bitCount = 0;
        int sp = srcOff;
        for (int i = 0; i < len; i++) {
            while (bitCount < bits) {
                acc |= ((long) (src[sp++] & 0xFF)) << bitCount;
                bitCount += 8;
            }
            out[i] = (int) (acc & mask);
            acc >>>= bits;
            bitCount -= bits;
        }
    }
}
