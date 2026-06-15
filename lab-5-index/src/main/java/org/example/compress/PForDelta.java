package org.example.compress;

import org.example.util.IntBuf;

import java.io.ByteArrayOutputStream;

public final class PForDelta {
    private PForDelta() {
    }

    private static int chooseBits(int[] v, int off, int len) {
        if (len == 0) {
            return 0;
        }
        int maxExceptions = len / 8;
        int[] histogram = new int[33];
        for (int i = 0; i < len; i++) {
            histogram[BitPack.bitsRequired(v[off + i])]++;
        }
        int covered = 0;
        for (int b = 0; b <= 32; b++) {
            covered += histogram[b];
            if (len - covered <= maxExceptions) {
                return b;
            }
        }
        return 32;
    }

    public static byte[] encode(int[] v, int off, int len) {
        int bits = chooseBits(v, off, len);
        long threshold = bits >= 32 ? (1L << 32) : (1L << bits);
        int mask = bits >= 32 ? 0xFFFFFFFF : (bits == 0 ? 0 : (1 << bits) - 1);

        int[] low = new int[len];
        IntBuf exPos = new IntBuf();
        IntBuf exVal = new IntBuf();
        for (int i = 0; i < len; i++) {
            low[i] = v[off + i] & mask;
            if ((v[off + i] & 0xFFFFFFFFL) >= threshold) {
                exPos.add(i);
                exVal.add(v[off + i]);
            }
        }

        byte[] packed = BitPack.pack(low, 0, len, bits);
        ByteArrayOutputStream out = new ByteArrayOutputStream(2 + packed.length + exPos.size * 5);
        out.write(bits);
        out.write(exPos.size);
        out.write(packed, 0, packed.length);
        for (int i = 0; i < exPos.size; i++) {
            out.write(exPos.a[i]);
            int value = exVal.a[i];
            out.write(value >>> 24);
            out.write(value >>> 16);
            out.write(value >>> 8);
            out.write(value);
        }
        return out.toByteArray();
    }

    public static int decode(byte[] src, int srcOff, int len, int[] out) {
        int p = srcOff;
        int bits = src[p++] & 0xFF;
        int numExceptions = src[p++] & 0xFF;
        BitPack.unpack(src, p, bits, len, out);
        p += BitPack.packedLength(len, bits);
        for (int i = 0; i < numExceptions; i++) {
            int pos = src[p++] & 0xFF;
            out[pos] = ((src[p] & 0xFF) << 24) | ((src[p + 1] & 0xFF) << 16)
                    | ((src[p + 2] & 0xFF) << 8) | (src[p + 3] & 0xFF);
            p += 4;
        }
        return p - srcOff;
    }
}
