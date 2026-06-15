package org.example.functional;

import org.example.index.IndexApi;
import org.example.index.RamIndex;
import org.example.index.DocIter;
import org.example.query.QEval;
import org.example.query.QParse;
import org.example.search.Finder;
import org.example.search.Finder.SearchResult;
import org.example.store.DiskIndex;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiskIndexTest {

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
            "NOT (term1 NEAR/2 term2)",
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
    void memoryAndDiskReturnIdenticalResults() throws Exception {
        Random r = new Random(7);
        int numDocs = 500;
        int vocab = 40;
        int docLen = 20;

        RamIndex.Builder b = new RamIndex.Builder();
        for (int d = 0; d < numDocs; d++) {
            StringBuilder sb = new StringBuilder();
            for (int t = 0; t < docLen; t++) {
                sb.append("term").append(r.nextInt(vocab)).append(' ');
            }
            b.addDocument(sb.toString());
        }
        RamIndex mem = b.build();

        Path file = Files.createTempFile("parity", ".idx");
        DiskIndex.write(mem, file);
        try (DiskIndex disk = DiskIndex.open(file)) {
            for (String q : QUERIES) {
                assertEquals(run(mem, q), run(disk, q), "match parity: " + q);
            }

            Finder memEngine = new Finder(mem);
            Finder diskEngine = new Finder(disk);
            for (String q : QUERIES) {
                List<SearchResult> mr = memEngine.search(q, 10);
                List<SearchResult> dr = diskEngine.search(q, 10);
                assertEquals(mr.size(), dr.size(), "result count: " + q);
                for (int i = 0; i < mr.size(); i++) {
                    assertEquals(mr.get(i).docId(), dr.get(i).docId(), "docId: " + q);
                    assertEquals(mr.get(i).score(), dr.get(i).score(), 1e-9, "score: " + q);
                }
            }
        }
        Files.deleteIfExists(file);
    }
}
