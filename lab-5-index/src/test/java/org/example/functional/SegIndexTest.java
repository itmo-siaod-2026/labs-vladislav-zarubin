package org.example.functional;

import org.example.index.IndexApi;
import org.example.index.RamIndex;
import org.example.index.DocIter;
import org.example.query.QEval;
import org.example.query.QParse;
import org.example.search.Finder;
import org.example.search.Finder.SearchResult;
import org.example.store.SegIndex;
import org.example.store.DiskIndex;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SegIndexTest {

    private static final String[] QUERIES = {
            "term1",
            "term2 AND term3",
            "term4 OR term5",
            "term6 AND NOT term7",
            "term8 ADJ term9",
            "term10 NEAR/5 term11",
            "(term1 OR term2) AND term3",
            "term3 ADJ term4 AND term5",
            "(term6 AND term7) AND NOT term8",
    };

    private static List<Integer> run(IndexApi idx, String query) {
        DocIter c = QEval.build(QParse.parse(query), idx);
        List<Integer> out = new ArrayList<>();
        for (int d = c.nextDoc(); d != DocIter.NO_MORE; d = c.nextDoc()) {
            out.add(d);
        }
        return out;
    }

    @Test
    void segmentedMatchesSingleIndex() throws Exception {
        Random r = new Random(11);
        int numDocs = 600;
        int vocab = 40;
        int docLen = 20;
        int segSize = 137;

        List<String> docs = new ArrayList<>();
        for (int d = 0; d < numDocs; d++) {
            StringBuilder sb = new StringBuilder();
            for (int t = 0; t < docLen; t++) {
                sb.append("term").append(r.nextInt(vocab)).append(' ');
            }
            docs.add(sb.toString());
        }

        RamIndex.Builder single = new RamIndex.Builder();
        docs.forEach(single::addDocument);
        Path singleFile = Files.createTempFile("single", ".idx");
        DiskIndex.write(single.build(), singleFile);

        Path dir = Files.createTempDirectory("segidx");
        int segNum = 0;
        for (int start = 0; start < numDocs; start += segSize) {
            RamIndex.Builder b = new RamIndex.Builder();
            for (int i = start; i < Math.min(start + segSize, numDocs); i++) {
                b.addDocument(docs.get(i));
            }
            DiskIndex.write(b.build(),
                    dir.resolve(String.format(Locale.ROOT, "segment-%05d.idx", segNum++)));
        }

        try (DiskIndex one = DiskIndex.open(singleFile);
             SegIndex multi = SegIndex.open(dir)) {
            assertEquals(one.numDocs(), multi.numDocs(), "numDocs");
            assertEquals(one.avgDocLength(), multi.avgDocLength(), 1e-9, "avgDocLength");

            for (String q : QUERIES) {
                assertEquals(run(one, q), run(multi, q), "match parity: " + q);
            }

            Finder es = new Finder(one);
            Finder em = new Finder(multi);
            for (String q : QUERIES) {
                List<SearchResult> rs = es.search(q, 10);
                List<SearchResult> rm = em.search(q, 10);
                assertEquals(rs.size(), rm.size(), "result count: " + q);
                for (int i = 0; i < rs.size(); i++) {
                    assertEquals(rs.get(i).docId(), rm.get(i).docId(), "docId: " + q);
                    assertEquals(rs.get(i).score(), rm.get(i).score(), 1e-9, "score: " + q);
                }
            }
        }

        Files.deleteIfExists(singleFile);
        try (var paths = Files.list(dir)) {
            for (Path p : paths.toList()) {
                Files.deleteIfExists(p);
            }
        }
        Files.deleteIfExists(dir);
    }
}
