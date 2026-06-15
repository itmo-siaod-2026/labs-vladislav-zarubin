package org.example.support;

import org.example.index.RamIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class MockDocs {
    private MockDocs() {
    }

    public static String[] vocabulary(int vocabSize) {
        String[] vocab = new String[vocabSize];
        for (int i = 0; i < vocabSize; i++) {
            vocab[i] = "term" + i;
        }
        return vocab;
    }

    public static List<String> documents(int numDocs, int docLength, int vocabSize, long seed) {
        Random random = new Random(seed);
        String[] vocab = vocabulary(vocabSize);
        List<String> documents = new ArrayList<>(numDocs);
        StringBuilder builder = new StringBuilder(docLength * 8);
        for (int d = 0; d < numDocs; d++) {
            builder.setLength(0);
            for (int t = 0; t < docLength; t++) {
                if (t > 0) {
                    builder.append(' ');
                }
                builder.append(vocab[skewedIndex(random, vocabSize)]);
            }
            documents.add(builder.toString());
        }
        return documents;
    }

    public static RamIndex buildIndex(List<String> documents) {
        RamIndex.Builder builder = new RamIndex.Builder();
        for (String document : documents) {
            builder.addDocument(document);
        }
        return builder.build();
    }

    public static String[] termQueries(int count, int vocabSize, long seed) {
        Random random = new Random(seed);
        int hi = freqHigh(vocabSize);
        String[] queries = new String[count];
        for (int i = 0; i < count; i++) {
            queries[i] = "term" + random.nextInt(hi);
        }
        return queries;
    }

    /** Частый + редкий терм; одни пары для AND/OR/NOT/ADJ/NEAR. */
    public static QueryPairs highLow(int count, int vocabSize, long seed) {
        int hi = freqHigh(vocabSize);
        int lo = rareLo(vocabSize);
        int rh = rareHi(vocabSize);
        return new QueryPairs(count, 0, hi, lo, rh, seed);
    }

    public static final class QueryPairs {
        private final int[][] terms;

        QueryPairs(int count, int loA, int hiA, int loB, int hiB, long seed) {
            if (hiA <= loA || hiB <= loB) {
                throw new IllegalArgumentException("empty term range");
            }
            Random random = new Random(seed);
            terms = new int[count][2];
            for (int i = 0; i < count; i++) {
                terms[i][0] = loA + random.nextInt(hiA - loA);
                terms[i][1] = loB + random.nextInt(hiB - loB);
            }
        }

        public String[] asAnd() {
            return format("term%d AND term%d");
        }

        public String[] asOr() {
            return format("term%d OR term%d");
        }

        public String[] asAndNot() {
            return format("term%d AND NOT term%d");
        }

        public String[] asAdj() {
            return format("term%d ADJ term%d");
        }

        public String[] asNear(int slop) {
            return format("term%d NEAR/" + slop + " term%d");
        }

        private String[] format(String template) {
            String[] queries = new String[terms.length];
            for (int i = 0; i < terms.length; i++) {
                queries[i] = String.format(template, terms[i][0], terms[i][1]);
            }
            return queries;
        }
    }

    static int freqHigh(int vocabSize) {
        return Math.max(10, vocabSize / 100);
    }

    static int rareLo(int vocabSize) {
        return Math.max(20, vocabSize / 3);
    }

    static int rareHi(int vocabSize) {
        return Math.max(rareLo(vocabSize) + 10, vocabSize / 2);
    }

    private static int skewedIndex(Random random, int vocabSize) {
        double u = random.nextDouble();
        int index = (int) (vocabSize * u * u);
        return index >= vocabSize ? vocabSize - 1 : index;
    }
}
