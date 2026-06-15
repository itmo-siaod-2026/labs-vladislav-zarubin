package org.example.index;

public interface DocIter {
    int NO_MORE = Integer.MAX_VALUE;

    DocIter EMPTY = new DocIter() {
        public int docID() {
            return NO_MORE;
        }

        public int nextDoc() {
            return NO_MORE;
        }

        public int advance(int target) {
            return NO_MORE;
        }

        public int freq() {
            return 0;
        }

        public int positions(int[] dst) {
            return 0;
        }
    };

    int docID();

    int nextDoc();

    int advance(int target);

    int freq();

    int positions(int[] dst);
}
