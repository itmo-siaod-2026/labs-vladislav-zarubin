package org.example.index;

import org.example.util.IntBuf;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

public final class RamIndex implements IndexApi {
    public final Map<String, PostingList> terms;
    public final int[] docLengths;
    private final int numDocs;
    private final double avgDocLength;

    private RamIndex(Map<String, PostingList> terms, int[] docLengths, long sumLengths) {
        this.terms = terms;
        this.docLengths = docLengths;
        this.numDocs = docLengths.length;
        this.avgDocLength = numDocs == 0 ? 0 : (double) sumLengths / numDocs;
    }

    @Override
    public DocIter termCursor(String term) {
        PostingList list = terms.get(term);
        return list == null ? DocIter.EMPTY : list.cursor();
    }

    @Override
    public int docFreq(String term) {
        PostingList list = terms.get(term);
        return list == null ? 0 : list.docIds.length;
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

    public static final class PostingList {
        public final int[] docIds;
        public final int[] freqs;
        public final int[] posOffsets;
        public final int[] positions;
        private final int step;
        private final int[] skipDocIds;

        PostingList(int[] docIds, int[] freqs, int[] positions) {
            this.docIds = docIds;
            this.freqs = freqs;
            this.positions = positions;
            this.posOffsets = new int[docIds.length + 1];
            for (int i = 0; i < docIds.length; i++) {
                posOffsets[i + 1] = posOffsets[i] + freqs[i];
            }
            this.step = Math.max(1, (int) Math.sqrt(docIds.length));
            this.skipDocIds = new int[(docIds.length + step - 1) / step];
            for (int s = 0; s < skipDocIds.length; s++) {
                skipDocIds[s] = docIds[s * step];
            }
        }

        DocIter cursor() {
            return new Cursor();
        }

        private final class Cursor implements DocIter {
            private int i = -1;

            public int docID() {
                return i < 0 ? -1 : i >= docIds.length ? NO_MORE : docIds[i];
            }

            public int nextDoc() {
                i++;
                return docID();
            }

            public int advance(int target) {
                if (i < 0) {
                    i = 0;
                }
                if (i >= docIds.length) {
                    return NO_MORE;
                }
                if (docIds[i] >= target) {
                    return docIds[i];
                }
                int s = i / step;
                while (s + 1 < skipDocIds.length && skipDocIds[s + 1] < target) {
                    s++;
                }
                if (s * step > i) {
                    i = s * step;
                }
                while (i < docIds.length && docIds[i] < target) {
                    i++;
                }
                return docID();
            }

            public int freq() {
                return freqs[i];
            }

            public int positions(int[] dst) {
                int from = posOffsets[i];
                int n = posOffsets[i + 1] - from;
                System.arraycopy(positions, from, dst, 0, n);
                return n;
            }
        }
    }

    public static final class Builder {
        private final Map<String, Acc> accumulators = new HashMap<>();
        private final IntBuf docLengths = new IntBuf();
        private long sumLengths = 0;
        private int nextDocId = 0;

        public int addDocument(String text) {
            int docId = nextDocId++;
            int length = 0;
            int start = -1;
            for (int i = 0; i <= text.length(); i++) {
                char c = i < text.length() ? text.charAt(i) : ' ';
                if (Character.isLetterOrDigit(c)) {
                    if (start < 0) {
                        start = i;
                    }
                } else if (start >= 0) {
                    String token = text.substring(start, i).toLowerCase(Locale.ROOT);
                    accumulators.computeIfAbsent(token, k -> new Acc()).add(docId, length++);
                    start = -1;
                }
            }
            docLengths.add(length);
            sumLengths += length;
            return docId;
        }

        public RamIndex build() {
            Map<String, PostingList> terms = new HashMap<>();
            Iterator<Map.Entry<String, Acc>> it = accumulators.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Acc> entry = it.next();
                Acc acc = entry.getValue();
                terms.put(entry.getKey(), new PostingList(
                        acc.docIds.trimmed(), acc.freqs.trimmed(), acc.positions.a));
                it.remove();
            }
            return new RamIndex(terms, docLengths.trimmed(), sumLengths);
        }

        private static final class Acc {
            final IntBuf docIds = new IntBuf();
            final IntBuf freqs = new IntBuf();
            final IntBuf positions = new IntBuf();

            void add(int docId, int position) {
                if (docIds.size == 0 || docIds.last() != docId) {
                    docIds.add(docId);
                    freqs.add(0);
                }
                freqs.incLast();
                positions.add(position);
            }
        }
    }
}
