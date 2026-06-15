package org.example.functional;

import org.example.index.RamIndex;
import org.example.index.DocIter;
import org.example.query.QTree;
import org.example.query.QEval;
import org.example.query.QParse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class QParseTest {

    private static List<Integer> run(RamIndex idx, String query) {
        DocIter c = QEval.build(QParse.parse(query), idx);
        List<Integer> out = new ArrayList<>();
        for (int d = c.nextDoc(); d != DocIter.NO_MORE; d = c.nextDoc()) {
            out.add(d);
        }
        return out;
    }

    @Test
    void parserBuildsExpectedTree() {
        assertInstanceOf(QTree.And.class, QParse.parse("a AND b"));
        assertInstanceOf(QTree.Or.class, QParse.parse("a OR b"));
        assertInstanceOf(QTree.Not.class, QParse.parse("NOT a"));
        assertInstanceOf(QTree.Phrase.class, QParse.parse("a ADJ b"));
        assertInstanceOf(QTree.And.class, QParse.parse("(a OR b) AND c"));
        assertInstanceOf(QTree.And.class, QParse.parse("(a AND b) AND NOT c"));
    }

    @Test
    void booleanOperatorsMatchBruteForce() {
        Random r = new Random(42);
        int numDocs = 300;
        int vocab = 30;
        int docLen = 12;

        List<List<String>> tokens = new ArrayList<>();
        RamIndex.Builder b = new RamIndex.Builder();
        for (int d = 0; d < numDocs; d++) {
            StringBuilder sb = new StringBuilder();
            List<String> toks = new ArrayList<>();
            for (int t = 0; t < docLen; t++) {
                String tok = "term" + r.nextInt(vocab);
                toks.add(tok);
                sb.append(tok).append(' ');
            }
            tokens.add(toks);
            b.addDocument(sb.toString());
        }
        RamIndex idx = b.build();

        Map<String, Set<Integer>> oracle = new HashMap<>();
        for (int d = 0; d < numDocs; d++) {
            for (String tok : tokens.get(d)) {
                oracle.computeIfAbsent(tok, k -> new TreeSet<>()).add(d);
            }
        }

        for (int trial = 0; trial < 60; trial++) {
            String ta = "term" + r.nextInt(vocab);
            String tb = "term" + r.nextInt(vocab);
            Set<Integer> A = oracle.getOrDefault(ta, Set.of());
            Set<Integer> B = oracle.getOrDefault(tb, Set.of());

            TreeSet<Integer> and = new TreeSet<>(A);
            and.retainAll(B);
            assertEquals(new ArrayList<>(and), run(idx, ta + " AND " + tb), "AND " + ta + " " + tb);

            TreeSet<Integer> or = new TreeSet<>(A);
            or.addAll(B);
            assertEquals(new ArrayList<>(or), run(idx, ta + " OR " + tb), "OR " + ta + " " + tb);

            TreeSet<Integer> diff = new TreeSet<>(A);
            diff.removeAll(B);
            assertEquals(new ArrayList<>(diff), run(idx, ta + " AND NOT " + tb), "ANDNOT " + ta + " " + tb);
        }
    }

    @Test
    void adjAndNearOnKnownCorpus() {
        RamIndex.Builder b = new RamIndex.Builder();
        b.addDocument("the quick brown fox");
        b.addDocument("brown quick the fox");
        b.addDocument("quick red brown thing");
        RamIndex idx = b.build();

        assertEquals(List.of(0), run(idx, "quick ADJ brown"));
        assertEquals(List.of(0, 1, 2), run(idx, "quick NEAR/2 brown"));
        assertEquals(List.of(0, 1), run(idx, "quick NEAR/1 brown"));
    }

    @Test
    void notAppliesToPhrase() {
        RamIndex.Builder b = new RamIndex.Builder();
        b.addDocument("new york city");
        b.addDocument("new jersey state");
        b.addDocument("brand new day");
        RamIndex idx = b.build();

        assertEquals(List.of(1, 2), run(idx, "new AND NOT (new NEAR/1 york)"));
        assertEquals(List.of(0), run(idx, "new NEAR/1 york"));
    }

    @Test
    void implicitAndIsNotSupported() {
        QTree parsed = QParse.parse("new NOT NEAR/1 york");
        assertInstanceOf(QTree.Term.class, parsed);
        assertEquals("new", ((QTree.Term) parsed).text());
    }
}
