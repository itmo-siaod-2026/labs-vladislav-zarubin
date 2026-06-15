package org.example.functional;

import org.example.compress.BitPack;
import org.example.compress.PForDelta;
import org.example.store.BlockCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CompressTest {

    @Test
    void bitPackingRoundTripAllWidths() {
        Random r = new Random(1);
        for (int bits = 1; bits <= 31; bits++) {
            int n = 200;
            int[] values = new int[n];
            long mask = (1L << bits) - 1;
            for (int i = 0; i < n; i++) {
                values[i] = (int) (r.nextLong() & mask);
            }
            byte[] packed = BitPack.pack(values, 0, n, bits);
            assertEquals(BitPack.packedLength(n, bits), packed.length, "bits=" + bits);
            int[] out = new int[n];
            BitPack.unpack(packed, 0, bits, n, out);
            assertArrayEquals(values, out, "bits=" + bits);
        }
    }

    @Test
    void pforDeltaRoundTripRandom() {
        Random r = new Random(2);
        for (int trial = 0; trial < 300; trial++) {
            int n = 1 + r.nextInt(255);
            int[] v = new int[n];
            for (int i = 0; i < n; i++) {
                v[i] = r.nextInt(100) < 5 ? r.nextInt(1 << 28) : r.nextInt(8);
            }
            byte[] enc = PForDelta.encode(v, 0, n);
            int[] out = new int[n];
            int consumed = PForDelta.decode(enc, 0, n, out);
            assertArrayEquals(v, out, "trial=" + trial);
            assertEquals(enc.length, consumed, "consumed bytes, trial=" + trial);
        }
    }

    @Test
    void blockCodecRoundTripRandom() {
        Random r = new Random(3);
        int n = 1000;
        int[] docIds = new int[n];
        int[] freqs = new int[n];
        int prev = -1;
        for (int i = 0; i < n; i++) {
            prev += 1 + r.nextInt(10);
            docIds[i] = prev;
            freqs[i] = 1 + r.nextInt(5);
        }
        int[] posOffsets = new int[n + 1];
        for (int i = 0; i < n; i++) {
            posOffsets[i + 1] = posOffsets[i] + freqs[i];
        }
        int[] positions = new int[posOffsets[n]];
        int p = 0;
        for (int i = 0; i < n; i++) {
            int pos = -1;
            for (int j = 0; j < freqs[i]; j++) {
                pos += 1 + r.nextInt(20);
                positions[p++] = pos;
            }
        }

        List<BlockCodec.EncodedBlock> blocks = BlockCodec.encode(docIds, freqs, posOffsets, positions);
        BlockCodec.DecodedBlock decoded = new BlockCodec.DecodedBlock();
        int idx = 0;
        int prevLast = -1;
        for (BlockCodec.EncodedBlock block : blocks) {
            BlockCodec.decode(block.bytes, prevLast, decoded);
            for (int k = 0; k < decoded.count; k++) {
                assertEquals(docIds[idx], decoded.docIds[k], "docId");
                assertEquals(freqs[idx], decoded.freqs[k], "freq");
                int from = posOffsets[idx];
                for (int j = 0; j < freqs[idx]; j++) {
                    assertEquals(positions[from + j],
                            decoded.positions[decoded.posOffsets[k] + j], "position");
                }
                idx++;
            }
            prevLast = block.lastDocId;
        }
        assertEquals(n, idx, "decoded count");
    }
}
