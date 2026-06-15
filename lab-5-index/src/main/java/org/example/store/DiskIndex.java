package org.example.store;

import org.example.compress.BitPack;
import org.example.index.IndexApi;
import org.example.index.RamIndex;
import org.example.index.DocIter;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DiskIndex implements IndexApi, AutoCloseable {
    private static final int MAGIC = 0x49565831;

    private final FileChannel channel;
    private final MappedByteBuffer map;
    private final Map<String, long[]> dictionary;
    private final long dictOffset;
    private final int dictionarySize;
    private final int numDocs;
    private final int[] docLengths;
    private final double avgDocLength;
    private Map<String, Long> termCache;

    private DiskIndex(FileChannel channel, MappedByteBuffer map, Map<String, long[]> dictionary,
                               long dictOffset, int dictionarySize,
                               int numDocs, int[] docLengths, double avgDocLength) {
        this.channel = channel;
        this.map = map;
        this.dictionary = dictionary;
        this.dictOffset = dictOffset;
        this.dictionarySize = dictionarySize;
        this.numDocs = numDocs;
        this.docLengths = docLengths;
        this.avgDocLength = avgDocLength;
    }

    public static void write(RamIndex index, Path path) throws IOException {
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(path)))) {
            out.writeInt(MAGIC);
            out.writeInt(index.numDocs());

            List<String> termList = List.copyOf(index.terms.keySet());
            long[] offsets = new long[termList.size()];
            int[] docFreqs = new int[termList.size()];
            for (int t = 0; t < termList.size(); t++) {
                RamIndex.PostingList list = index.terms.get(termList.get(t));
                offsets[t] = out.size();
                docFreqs[t] = list.docIds.length;
                List<BlockCodec.EncodedBlock> blocks = BlockCodec.encode(
                        list.docIds, list.freqs, list.posOffsets, list.positions);
                out.writeInt(blocks.size());
                for (BlockCodec.EncodedBlock block : blocks) {
                    out.writeInt(block.lastDocId);
                    out.writeInt(block.bytes.length);
                }
                for (BlockCodec.EncodedBlock block : blocks) {
                    out.write(block.bytes);
                }
            }

            long docLenOffset = out.size();
            int[] docLengths = index.docLengths;
            int width = BitPack.maxBits(docLengths, 0, docLengths.length);
            out.writeInt(docLengths.length);
            out.writeByte(width);
            out.write(BitPack.pack(docLengths, 0, docLengths.length, width));

            long dictOffset = out.size();
            out.writeInt(termList.size());
            for (int t = 0; t < termList.size(); t++) {
                byte[] termBytes = termList.get(t).getBytes(StandardCharsets.UTF_8);
                out.writeInt(termBytes.length);
                out.write(termBytes);
                out.writeInt(docFreqs[t]);
                out.writeLong(offsets[t]);
            }

            out.writeLong(dictOffset);
            out.writeLong(docLenOffset);
            out.writeInt(MAGIC);
        }
    }

    public static DiskIndex open(Path path) throws IOException {
        return open(path, false);
    }

    public static DiskIndex openLazy(Path path) throws IOException {
        return open(path, true);
    }

    private static DiskIndex open(Path path, boolean lazyDictionary) throws IOException {
        FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
        long size = channel.size();
        MappedByteBuffer map = channel.map(FileChannel.MapMode.READ_ONLY, 0, size);
        map.order(ByteOrder.BIG_ENDIAN);

        ByteBuffer reader = map.duplicate().order(ByteOrder.BIG_ENDIAN);
        reader.getInt();
        int numDocs = reader.getInt();

        reader.position((int) (size - 20));
        long dictOffset = reader.getLong();
        long docLenOffset = reader.getLong();

        reader.position((int) docLenOffset);
        int lengthCount = reader.getInt();
        int width = reader.get() & 0xFF;
        byte[] packed = new byte[BitPack.packedLength(lengthCount, width)];
        reader.get(packed);
        int[] docLengths = new int[lengthCount];
        BitPack.unpack(packed, 0, width, lengthCount, docLengths);
        long sum = 0;
        for (int length : docLengths) {
            sum += length;
        }
        double avgDocLength = lengthCount == 0 ? 0 : (double) sum / lengthCount;

        reader.position((int) dictOffset);
        int numTerms = reader.getInt();
        Map<String, long[]> dictionary = null;
        if (!lazyDictionary) {
            dictionary = new HashMap<>(numTerms * 2);
            for (int t = 0; t < numTerms; t++) {
                byte[] termBytes = new byte[reader.getInt()];
                reader.get(termBytes);
                dictionary.put(new String(termBytes, StandardCharsets.UTF_8),
                        new long[]{reader.getInt(), reader.getLong()});
            }
        }

        return new DiskIndex(channel, map, dictionary, dictOffset, numTerms,
                numDocs, docLengths, avgDocLength);
    }

    @Override
    public DocIter termCursor(String term) {
        long meta = termMeta(term);
        return meta < 0 ? DocIter.EMPTY
                : new Cursor(map.duplicate().order(ByteOrder.BIG_ENDIAN), metaOffset(meta));
    }

    @Override
    public int docFreq(String term) {
        long meta = termMeta(term);
        return meta < 0 ? 0 : metaDocFreq(meta);
    }

    @Override
    public int numDocs() {
        return numDocs;
    }

    @Override
    public int docLength(int docId) {
        return docLengths[docId];
    }

    @Override
    public double avgDocLength() {
        return avgDocLength;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    public record Stats(int numDocs, int dictionarySize, long totalPostings, long totalPositions) {
    }

    public Stats stats() {
        long postings = 0;
        if (dictionary != null) {
            for (long[] meta : dictionary.values()) {
                postings += meta[0];
            }
        } else {
            ByteBuffer reader = map.duplicate().order(ByteOrder.BIG_ENDIAN);
            reader.position((int) dictOffset);
            int numTerms = reader.getInt();
            for (int t = 0; t < numTerms; t++) {
                int len = reader.getInt();
                reader.position(reader.position() + len);
                postings += reader.getInt();
                reader.getLong();
            }
        }
        long positions = 0;
        for (int len : docLengths) {
            positions += len;
        }
        return new Stats(numDocs, dictionarySize, postings, positions);
    }

    private long termMeta(String term) {
        if (dictionary != null) {
            long[] meta = dictionary.get(term);
            return meta == null ? -1 : packMeta((int) meta[0], meta[1]);
        }

        if (termCache != null) {
            Long cached = termCache.get(term);
            if (cached != null) {
                return cached;
            }
        }

        byte[] target = term.getBytes(StandardCharsets.UTF_8);
        ByteBuffer reader = map.duplicate().order(ByteOrder.BIG_ENDIAN);
        reader.position((int) dictOffset);
        int numTerms = reader.getInt();
        for (int t = 0; t < numTerms; t++) {
            int len = reader.getInt();
            boolean match = len == target.length;
            for (int i = 0; i < len; i++) {
                byte b = reader.get();
                if (match && b != target[i]) {
                    match = false;
                }
            }
            int docFreq = reader.getInt();
            long offset = reader.getLong();
            if (match) {
                long meta = packMeta(docFreq, offset);
                cacheTerm(term, meta);
                return meta;
            }
        }
        cacheTerm(term, -1);
        return -1;
    }

    private void cacheTerm(String term, long meta) {
        if (termCache == null) {
            termCache = new HashMap<>();
        }
        termCache.put(term, meta);
    }

    private static long packMeta(int docFreq, long offset) {
        return (offset << 32) | (docFreq & 0xFFFFFFFFL);
    }

    private static int metaDocFreq(long meta) {
        return (int) meta;
    }

    private static long metaOffset(long meta) {
        return meta >>> 32;
    }

    private static final class Cursor implements DocIter {
        private final ByteBuffer buf;
        private final int numBlocks;
        private final int[] skipLastDoc;
        private final int[] blockLen;
        private final long[] blockOffset;
        private final BlockCodec.DecodedBlock block = new BlockCodec.DecodedBlock();
        private final byte[] scratch;
        private int curBlock = -1;
        private int i = -1;

        Cursor(ByteBuffer buf, long postingsOffset) {
            this.buf = buf;
            buf.position((int) postingsOffset);
            this.numBlocks = buf.getInt();
            this.skipLastDoc = new int[numBlocks];
            this.blockLen = new int[numBlocks];
            this.blockOffset = new long[numBlocks];
            long offset = postingsOffset + 4 + (long) numBlocks * 8;
            int maxLen = 1;
            for (int b = 0; b < numBlocks; b++) {
                skipLastDoc[b] = buf.getInt();
                blockLen[b] = buf.getInt();
                blockOffset[b] = offset;
                offset += blockLen[b];
                maxLen = Math.max(maxLen, blockLen[b]);
            }
            this.scratch = new byte[maxLen];
        }

        private void loadBlock(int b) {
            buf.position((int) blockOffset[b]);
            buf.get(scratch, 0, blockLen[b]);
            BlockCodec.decode(scratch, b == 0 ? -1 : skipLastDoc[b - 1], block);
            curBlock = b;
            i = 0;
        }

        public int docID() {
            return curBlock < 0 ? -1 : curBlock >= numBlocks ? NO_MORE : block.docIds[i];
        }

        public int nextDoc() {
            if (curBlock < 0) {
                loadBlock(0);
                return block.docIds[0];
            }
            if (curBlock >= numBlocks) {
                return NO_MORE;
            }
            if (++i >= block.count) {
                if (curBlock + 1 >= numBlocks) {
                    curBlock = numBlocks;
                    return NO_MORE;
                }
                loadBlock(curBlock + 1);
            }
            return block.docIds[i];
        }

        public int advance(int target) {
            if (curBlock >= numBlocks) {
                return NO_MORE;
            }
            if (curBlock >= 0 && i < block.count && block.docIds[i] >= target) {
                return block.docIds[i];
            }
            int b = curBlock < 0 ? 0 : curBlock;
            while (b < numBlocks && skipLastDoc[b] < target) {
                b++;
            }
            if (b >= numBlocks) {
                curBlock = numBlocks;
                return NO_MORE;
            }
            if (b != curBlock) {
                loadBlock(b);
            }
            while (i < block.count && block.docIds[i] < target) {
                i++;
            }
            return block.docIds[i];
        }

        public int freq() {
            return block.freqs[i];
        }

        public int positions(int[] dst) {
            int from = block.posOffsets[i];
            int n = block.posOffsets[i + 1] - from;
            System.arraycopy(block.positions, from, dst, 0, n);
            return n;
        }
    }
}
