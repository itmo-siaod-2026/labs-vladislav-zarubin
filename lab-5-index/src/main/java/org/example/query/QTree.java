package org.example.query;

import java.util.List;

public interface QTree {

    record Term(String text) implements QTree {
    }

    record And(List<QTree> parts) implements QTree {
    }

    record Or(List<QTree> parts) implements QTree {
    }

    record Not(QTree child) implements QTree {
    }

    record Phrase(List<String> terms, int slop, boolean ordered) implements QTree {
    }
}
