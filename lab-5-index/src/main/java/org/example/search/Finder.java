package org.example.search;

import org.example.index.IndexApi;
import org.example.index.DocIter;
import org.example.query.QTree;
import org.example.query.QEval;
import org.example.query.QParse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.PriorityQueue;

public final class Finder {
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    private final IndexApi index;

    public Finder(IndexApi index) {
        this.index = index;
    }

    public record SearchResult(int docId, double score) {
    }

    public int countMatches(String query) {
        DocIter matches = QEval.build(QParse.parse(query), index);
        int count = 0;
        for (int doc = matches.nextDoc(); doc != DocIter.NO_MORE; doc = matches.nextDoc()) {
            count++;
        }
        return count;
    }

    public List<SearchResult> search(String query, int topK) {
        QTree parsed = QParse.parse(query);
        DocIter matches = QEval.build(parsed, index);

        List<String> terms = new ArrayList<>(new LinkedHashSet<>(QParse.terms(parsed)));
        int numDocs = index.numDocs();
        DocIter[] scorers = new DocIter[terms.size()];
        double[] idf = new double[terms.size()];
        for (int t = 0; t < terms.size(); t++) {
            scorers[t] = index.termCursor(terms.get(t));
            idf[t] = idf(numDocs, index.docFreq(terms.get(t)));
        }
        double avgDocLength = index.avgDocLength();

        PriorityQueue<SearchResult> heap = new PriorityQueue<>(Comparator.comparingDouble(SearchResult::score));
        for (int doc = matches.nextDoc(); doc != DocIter.NO_MORE; doc = matches.nextDoc()) {
            int length = index.docLength(doc);
            double score = 0;
            for (int t = 0; t < scorers.length; t++) {
                int d = scorers[t].docID();
                if (d < doc) {
                    d = scorers[t].advance(doc);
                }
                if (d == doc) {
                    score += score(scorers[t].freq(), length, avgDocLength, idf[t]);
                }
            }
            if (heap.size() < topK) {
                heap.add(new SearchResult(doc, score));
            } else if (!heap.isEmpty() && heap.peek().score() < score) {
                heap.poll();
                heap.add(new SearchResult(doc, score));
            }
        }

        List<SearchResult> results = new ArrayList<>(heap);
        results.sort(Comparator.comparingDouble(SearchResult::score).reversed());
        return results;
    }

    private static double idf(int numDocs, int docFreq) {
        return Math.log(1 + (numDocs - docFreq + 0.5) / (docFreq + 0.5));
    }

    private static double score(int tf, int docLength, double avgDocLength, double idf) {
        double norm = avgDocLength == 0 ? 0 : docLength / avgDocLength;
        return idf * (tf * (K1 + 1)) / (tf + K1 * (1 - B + B * norm));
    }
}
