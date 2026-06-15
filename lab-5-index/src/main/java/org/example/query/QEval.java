package org.example.query;

import org.example.index.IndexApi;
import org.example.index.DocIter;

import java.util.List;

public final class QEval {
    private QEval() {
    }

    public static DocIter build(QTree query, IndexApi index) {
        if (query instanceof QTree.Term term) {
            return index.termCursor(term.text());
        }
        if (query instanceof QTree.And and) {
            List<QTree> parts = and.parts();
            return parts.size() == 1 ? build(parts.get(0), index) : new And(buildAll(parts, index));
        }
        if (query instanceof QTree.Or or) {
            List<QTree> parts = or.parts();
            return parts.size() == 1 ? build(parts.get(0), index) : new Or(buildAll(parts, index));
        }
        if (query instanceof QTree.Not not) {
            return new Not(build(not.child(), index), index.numDocs());
        }
        if (query instanceof QTree.Phrase phrase) {
            DocIter[] subs = new DocIter[phrase.terms().size()];
            for (int i = 0; i < subs.length; i++) {
                subs[i] = index.termCursor(phrase.terms().get(i));
            }
            return new Phrase(subs, phrase.slop(), phrase.ordered());
        }
        return DocIter.EMPTY;
    }

    private static DocIter[] buildAll(List<QTree> parts, IndexApi index) {
        DocIter[] subs = new DocIter[parts.size()];
        for (int i = 0; i < subs.length; i++) {
            subs[i] = build(parts.get(i), index);
        }
        return subs;
    }

    private static final class And implements DocIter {
        private final DocIter[] subs;
        private int doc = -1;

        And(DocIter[] subs) {
            this.subs = subs;
        }

        public int docID() {
            return doc;
        }

        public int nextDoc() {
            return align(subs[0].nextDoc());
        }

        public int advance(int target) {
            return align(subs[0].advance(target));
        }

        private int align(int candidate) {
            while (candidate != NO_MORE) {
                boolean all = true;
                for (DocIter sub : subs) {
                    int d = sub.docID();
                    if (d < candidate) {
                        d = sub.advance(candidate);
                    }
                    if (d > candidate) {
                        candidate = d;
                        all = false;
                        break;
                    }
                }
                if (all) {
                    return doc = candidate;
                }
            }
            return doc = NO_MORE;
        }

        public int freq() {
            return 0;
        }

        public int positions(int[] dst) {
            return 0;
        }
    }

    private static final class Or implements DocIter {
        private final DocIter[] subs;
        private int doc = -1;

        Or(DocIter[] subs) {
            this.subs = subs;
        }

        public int docID() {
            return doc;
        }

        public int nextDoc() {
            int min = NO_MORE;
            for (DocIter sub : subs) {
                int d = sub.docID();
                if (d <= doc) {
                    d = sub.nextDoc();
                }
                if (d < min) {
                    min = d;
                }
            }
            return doc = min;
        }

        public int advance(int target) {
            int min = NO_MORE;
            for (DocIter sub : subs) {
                int d = sub.docID();
                if (d < target) {
                    d = sub.advance(target);
                }
                if (d < min) {
                    min = d;
                }
            }
            return doc = min;
        }

        public int freq() {
            return 0;
        }

        public int positions(int[] dst) {
            return 0;
        }
    }

    private static final class Not implements DocIter {
        private final DocIter child;
        private final int numDocs;
        private int doc = -1;

        Not(DocIter child, int numDocs) {
            this.child = child;
            this.numDocs = numDocs;
        }

        public int docID() {
            return doc;
        }

        public int nextDoc() {
            return advance(doc + 1);
        }

        public int advance(int target) {
            for (int d = target; d < numDocs; d++) {
                int c = child.docID();
                if (c < d) {
                    c = child.advance(d);
                }
                if (c != d) {
                    return doc = d;
                }
            }
            return doc = NO_MORE;
        }

        public int freq() {
            return 0;
        }

        public int positions(int[] dst) {
            return 0;
        }
    }

    private static final class Phrase implements DocIter {
        private final DocIter[] terms;
        private final And docs;
        private final int slop;
        private final boolean ordered;
        private final int[][] pos;
        private final int[] counts;
        private final int[] pointers;
        private int doc = -1;

        Phrase(DocIter[] terms, int slop, boolean ordered) {
            this.terms = terms;
            this.docs = new And(terms);
            this.slop = slop;
            this.ordered = ordered;
            this.pos = new int[terms.length][16];
            this.counts = new int[terms.length];
            this.pointers = new int[terms.length];
        }

        public int docID() {
            return doc;
        }

        public int nextDoc() {
            return scan(docs.nextDoc());
        }

        public int advance(int target) {
            return scan(docs.advance(target));
        }

        private int scan(int candidate) {
            while (candidate != NO_MORE) {
                if (matches()) {
                    return doc = candidate;
                }
                candidate = docs.nextDoc();
            }
            return doc = NO_MORE;
        }

        private boolean matches() {
            for (int k = 0; k < terms.length; k++) {
                int f = terms[k].freq();
                if (pos[k].length < f) {
                    pos[k] = new int[f];
                }
                counts[k] = terms[k].positions(pos[k]);
            }
            return ordered ? matchesOrdered() : matchesUnordered();
        }

        private boolean matchesOrdered() {
            for (int a = 0; a < counts[0]; a++) {
                int prev = pos[0][a];
                boolean ok = true;
                for (int k = 1; k < terms.length; k++) {
                    int next = firstGreater(pos[k], counts[k], prev);
                    if (next == NO_MORE || next - prev > slop) {
                        ok = false;
                        break;
                    }
                    prev = next;
                }
                if (ok) {
                    return true;
                }
            }
            return false;
        }

        private boolean matchesUnordered() {
            for (int k = 0; k < terms.length; k++) {
                pointers[k] = 0;
            }
            while (true) {
                int min = Integer.MAX_VALUE;
                int max = Integer.MIN_VALUE;
                int minK = -1;
                for (int k = 0; k < terms.length; k++) {
                    if (pointers[k] >= counts[k]) {
                        return false;
                    }
                    int v = pos[k][pointers[k]];
                    if (v < min) {
                        min = v;
                        minK = k;
                    }
                    if (v > max) {
                        max = v;
                    }
                }
                if (max - min <= slop) {
                    return true;
                }
                pointers[minK]++;
            }
        }

        private static int firstGreater(int[] values, int count, int bound) {
            for (int i = 0; i < count; i++) {
                if (values[i] > bound) {
                    return values[i];
                }
            }
            return NO_MORE;
        }

        public int freq() {
            return 1;
        }

        public int positions(int[] dst) {
            return 0;
        }
    }
}
