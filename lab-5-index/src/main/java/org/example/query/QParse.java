package org.example.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class QParse {
    private final List<String> tokens;
    private int pos;

    private QParse(List<String> tokens) {
        this.tokens = tokens;
    }

    public static QTree parse(String query) {
        return new QParse(lex(query)).parseOr();
    }

    public static List<String> terms(QTree query) {
        List<String> out = new ArrayList<>();
        collectTerms(query, out);
        return out;
    }

    private static void collectTerms(QTree query, List<String> out) {
        if (query instanceof QTree.Term term) {
            out.add(term.text());
        } else if (query instanceof QTree.And and) {
            and.parts().forEach(p -> collectTerms(p, out));
        } else if (query instanceof QTree.Or or) {
            or.parts().forEach(p -> collectTerms(p, out));
        } else if (query instanceof QTree.Not not) {
            collectTerms(not.child(), out);
        } else if (query instanceof QTree.Phrase phrase) {
            out.addAll(phrase.terms());
        }
    }

    private static List<String> lex(String query) {
        List<String> tokens = new ArrayList<>();
        int start = -1;
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (c == '(' || c == ')') {
                if (start >= 0) {
                    tokens.add(query.substring(start, i));
                    start = -1;
                }
                tokens.add(String.valueOf(c));
            } else if (Character.isWhitespace(c)) {
                if (start >= 0) {
                    tokens.add(query.substring(start, i));
                    start = -1;
                }
            } else if (start < 0) {
                start = i;
            }
        }
        if (start >= 0) {
            tokens.add(query.substring(start));
        }
        return tokens;
    }

    private QTree parseOr() {
        QTree left = parseAnd();
        if (!isOr(peek())) {
            return left;
        }
        List<QTree> parts = new ArrayList<>();
        parts.add(left);
        while (isOr(peek())) {
            next();
            parts.add(parseAnd());
        }
        return new QTree.Or(parts);
    }

    private QTree parseAnd() {
        QTree left = parseNot();
        if (!isAnd(peek())) {
            return left;
        }
        List<QTree> parts = new ArrayList<>();
        parts.add(left);
        while (isAnd(peek())) {
            next();
            parts.add(parseNot());
        }
        return new QTree.And(parts);
    }

    private QTree parseNot() {
        if (isNot(peek())) {
            next();
            return new QTree.Not(parseNot());
        }
        return parsePhrase();
    }

    private QTree parsePhrase() {
        QTree first = parsePrimary();
        if (!(first instanceof QTree.Term firstTerm) || !isProx(peek())) {
            return first;
        }
        List<String> terms = new ArrayList<>();
        terms.add(firstTerm.text());
        boolean ordered = true;
        int slop = 1;
        while (isProx(peek())) {
            String op = next();
            ordered = op.toLowerCase(Locale.ROOT).startsWith("adj");
            slop = slopOf(op, ordered);
            if (!(parsePrimary() instanceof QTree.Term term)) {
                break;
            }
            terms.add(term.text());
        }
        return new QTree.Phrase(terms, slop, ordered);
    }

    private QTree parsePrimary() {
        String token = next();
        if ("(".equals(token)) {
            QTree inner = parseOr();
            if (")".equals(peek())) {
                next();
            }
            return inner;
        }
        return new QTree.Term(token.toLowerCase(Locale.ROOT));
    }

    private static int slopOf(String op, boolean ordered) {
        int slash = op.indexOf('/');
        return slash >= 0 ? Integer.parseInt(op.substring(slash + 1)) : ordered ? 1 : 8;
    }

    private String peek() {
        return pos < tokens.size() ? tokens.get(pos) : null;
    }

    private String next() {
        return tokens.get(pos++);
    }

    private static boolean isAnd(String token) {
        return "and".equalsIgnoreCase(token);
    }

    private static boolean isOr(String token) {
        return "or".equalsIgnoreCase(token);
    }

    private static boolean isNot(String token) {
        return "not".equalsIgnoreCase(token);
    }

    private static boolean isProx(String token) {
        if (token == null) {
            return false;
        }
        String lower = token.toLowerCase(Locale.ROOT);
        return lower.equals("adj") || lower.equals("near")
                || lower.startsWith("adj/") || lower.startsWith("near/");
    }
}
