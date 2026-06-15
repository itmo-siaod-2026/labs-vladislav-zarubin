package org.example.store;

import org.example.index.IndexApi;
import org.example.index.DocIter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;


public final class SegIndex implements IndexApi, AutoCloseable {
    private final DiskIndex[] segments;
    private final int[] base;
    private final int numDocs;
    private final double avgDocLength;

    private SegIndex(DiskIndex[] segments, int[] base, double avgDocLength) {
        this.segments = segments;
        this.base = base;
        this.numDocs = base[base.length - 1];
        this.avgDocLength = avgDocLength;
    }

    public static SegIndex open(Path dir) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> {
                String name = p.getFileName().toString();
                return name.startsWith("segment-") && name.endsWith(".idx");
            }).sorted().forEach(files::add);
        }
        DiskIndex[] segments = new DiskIndex[files.size()];
        int[] base = new int[segments.length + 1];
        double totalTokens = 0;
        for (int i = 0; i < segments.length; i++) {
            segments[i] = DiskIndex.openLazy(files.get(i));
            base[i + 1] = base[i] + segments[i].numDocs();
            totalTokens += segments[i].avgDocLength() * segments[i].numDocs();
        }
        int total = base[segments.length];
        return new SegIndex(segments, base, total == 0 ? 0 : totalTokens / total);
    }

    public int segmentCount() {
        return segments.length;
    }

    @Override
    public DocIter termCursor(String term) {
        return new Cursor(term);
    }

    @Override
    public int docFreq(String term) {
        int total = 0;
        for (DiskIndex segment : segments) {
            total += segment.docFreq(term);
        }
        return total;
    }

    @Override
    public int numDocs() {
        return numDocs;
    }

    @Override
    public int docLength(int docId) {
        int s = segmentOf(docId);
        return segments[s].docLength(docId - base[s]);
    }

    @Override
    public double avgDocLength() {
        return avgDocLength;
    }

    @Override
    public void close() throws IOException {
        for (DiskIndex segment : segments) {
            segment.close();
        }
    }

    private int segmentOf(int globalId) {
        int lo = 0;
        int hi = segments.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (base[mid] <= globalId) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    private final class Cursor implements DocIter {
        private final String term;
        private int seg = -1;
        private DocIter cur;
        private int doc = -1;

        Cursor(String term) {
            this.term = term;
        }

        @Override
        public int docID() {
            return doc;
        }

        @Override
        public int nextDoc() {
            if (seg < 0) {
                seg = 0;
            }
            while (seg < segments.length) {
                if (cur == null) {
                    cur = segments[seg].termCursor(term);
                }
                int d = cur.nextDoc();
                if (d != NO_MORE) {
                    return doc = base[seg] + d;
                }
                seg++;
                cur = null;
            }
            return doc = NO_MORE;
        }

        @Override
        public int advance(int target) {
            if (seg < 0) {
                seg = 0;
            }
            if (seg < segments.length && base[seg + 1] <= target) {
                while (seg < segments.length && base[seg + 1] <= target) {
                    seg++;
                }
                cur = null;
            }
            while (seg < segments.length) {
                if (cur == null) {
                    cur = segments[seg].termCursor(term);
                }
                int local = target - base[seg];
                int d = cur.advance(local < 0 ? 0 : local);
                if (d != NO_MORE) {
                    return doc = base[seg] + d;
                }
                seg++;
                cur = null;
            }
            return doc = NO_MORE;
        }

        @Override
        public int freq() {
            return cur.freq();
        }

        @Override
        public int positions(int[] dst) {
            return cur.positions(dst);
        }
    }
}
