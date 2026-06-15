package org.example.index;

public interface IndexApi {
    DocIter termCursor(String term);

    int docFreq(String term);

    int numDocs();

    int docLength(int docId);

    double avgDocLength();
}
