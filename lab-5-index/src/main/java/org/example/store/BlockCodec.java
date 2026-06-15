package org.example.store;

import org.example.compress.BitPack;
import org.example.compress.PForDelta;
import org.example.util.IntBuf;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class BlockCodec {
    public static final int BLOCK_SIZE = 128;

    private BlockCodec() {
    }

    public static final class EncodedBlock {
        public byte[] bytes;
        public int lastDocId;
    }

    public static List<EncodedBlock> encode(int[] docIds, int[] freqs, int[] posOffsets, int[] positions) {
        int n = docIds.length;
        List<EncodedBlock> blocks = new ArrayList<>();
        for (int start = 0; start < n; start += BLOCK_SIZE) {
            int end = Math.min(start + BLOCK_SIZE, n);
            int count = end - start;

            int[] gaps = new int[count];
            int prev = start == 0 ? -1 : docIds[start - 1];
            for (int k = 0; k < count; k++) {
                gaps[k] = docIds[start + k] - prev - 1;
                prev = docIds[start + k];
            }

            int[] freqMinusOne = new int[count];
            for (int k = 0; k < count; k++) {
                freqMinusOne[k] = freqs[start + k] - 1;
            }

            IntBuf posGaps = new IntBuf();
            for (int k = 0; k < count; k++) {
                int prevPos = -1;
                for (int j = posOffsets[start + k]; j < posOffsets[start + k + 1]; j++) {
                    posGaps.add(prevPos < 0 ? positions[j] : positions[j] - prevPos - 1);
                    prevPos = positions[j];
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(count);
            byte[] docBytes = PForDelta.encode(gaps, 0, count);
            out.write(docBytes, 0, docBytes.length);

            int freqBits = BitPack.maxBits(freqMinusOne, 0, count);
            out.write(freqBits);
            byte[] freqBytes = BitPack.pack(freqMinusOne, 0, count, freqBits);
            out.write(freqBytes, 0, freqBytes.length);

            int posBits = BitPack.maxBits(posGaps.a, 0, posGaps.size);
            out.write(posBits);
            byte[] posBytes = BitPack.pack(posGaps.a, 0, posGaps.size, posBits);
            out.write(posBytes, 0, posBytes.length);

            EncodedBlock block = new EncodedBlock();
            block.bytes = out.toByteArray();
            block.lastDocId = docIds[end - 1];
            blocks.add(block);
        }
        return blocks;
    }

    public static final class DecodedBlock {
        public int count;
        public final int[] docIds = new int[BLOCK_SIZE];
        public final int[] freqs = new int[BLOCK_SIZE];
        public final int[] posOffsets = new int[BLOCK_SIZE + 1];
        public int[] positions = new int[BLOCK_SIZE];
        private final int[] scratch = new int[BLOCK_SIZE];
    }

    public static void decode(byte[] src, int prevLastDocId, DecodedBlock out) {
        int p = 0;
        int count = src[p++] & 0xFF;
        out.count = count;

        p += PForDelta.decode(src, p, count, out.scratch);
        int prev = prevLastDocId;
        for (int k = 0; k < count; k++) {
            prev += out.scratch[k] + 1;
            out.docIds[k] = prev;
        }

        int freqBits = src[p++] & 0xFF;
        BitPack.unpack(src, p, freqBits, count, out.freqs);
        p += BitPack.packedLength(count, freqBits);
        out.posOffsets[0] = 0;
        int total = 0;
        for (int k = 0; k < count; k++) {
            out.freqs[k] += 1;
            total += out.freqs[k];
            out.posOffsets[k + 1] = total;
        }

        int posBits = src[p++] & 0xFF;
        if (out.positions.length < total) {
            out.positions = new int[total];
        }
        BitPack.unpack(src, p, posBits, total, out.positions);
        for (int k = 0; k < count; k++) {
            int prevPos = -1;
            for (int j = out.posOffsets[k]; j < out.posOffsets[k + 1]; j++) {
                int position = prevPos < 0 ? out.positions[j] : prevPos + out.positions[j] + 1;
                out.positions[j] = position;
                prevPos = position;
            }
        }
    }
}
