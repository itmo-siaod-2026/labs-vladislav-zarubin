package org.example;

import org.example.index.RamIndex;
import org.example.query.QParse;
import org.example.search.Finder;
import org.example.search.Finder.SearchResult;
import org.example.store.SegIndex;
import org.example.store.DiskIndex;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.JSplitPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.io.BufferedInputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;

import static javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD;
import static javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA;
import static javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

public final class WikiMain {
    private static final int PREVIEW_LEN = 200;
    private static final String DEFAULT_WIKI_BASE = "https://ru.wikipedia.org";
    private static final String[] XML_SIZE_LIMITS = {
            "jdk.xml.totalEntitySizeLimit",
            "jdk.xml.maxGeneralEntitySizeLimit",
            "jdk.xml.maxParameterEntitySizeLimit",
            "jdk.xml.maxOverallEntitySizeLimit",
    };

    static {
        for (String key : XML_SIZE_LIMITS) {
            System.setProperty(key, "0");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: Wiki index <dump.xml> <output.idx> [limit] [wikiBase]");
            System.err.println("       Wiki search <index.idx> <topK> <query...>");
            System.err.println("       Wiki repl <index.idx | segment-dir> [topK]");
            System.err.println("       Wiki ui <index.idx | segment-dir> [wikiBase]");
            System.err.println("       Wiki stats <index.idx> [term...]");
            System.err.println("       Wiki segindex <dump.xml> <dir> [limit] [segmentSize] [wikiBase]");
            System.err.println("       Wiki segsearch <dir> <topK> <query...>");
            System.err.println("  wikiBase example: https://ru.wikipedia.org");
            return;
        }
        switch (args[0]) {
            case "index" -> {
                Path output = Paths.get(args[2]);
                int limit = args.length > 3 && !args[3].startsWith("http")
                        ? Integer.parseInt(args[3]) : Integer.MAX_VALUE;
                String wikiBase = wikiBaseArg(args, 3);
                index(Paths.get(args[1]), output, limit, wikiBase);
            }
            case "search" -> search(
                    Paths.get(args[1]), Integer.parseInt(args[2]),
                    String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
            case "repl" -> {
                Path path = Paths.get(args[1]);
                int topK = args.length > 2 ? Integer.parseInt(args[2]) : 20;
                if (Files.isDirectory(path)) {
                    segRepl(path, topK);
                } else {
                    repl(path, topK);
                }
            }
            case "ui" -> {
                Path path = Paths.get(args[1]);
                String wikiBase = args.length > 2 ? normalizeWikiBase(args[2]) : readWikiBase(path);
                if (Files.isDirectory(path)) {
                    segUi(path, wikiBase);
                } else {
                    ui(path, wikiBase);
                }
            }
            case "stats" -> stats(Paths.get(args[1]), Arrays.copyOfRange(args, 2, args.length));
            case "segindex" -> {
                int limit = args.length > 3 ? Integer.parseInt(args[3]) : Integer.MAX_VALUE;
                int segSize = args.length > 4 && !args[4].startsWith("http")
                        ? Integer.parseInt(args[4]) : 50_000;
                String wikiBase = wikiBaseArg(args, 4);
                segIndex(Paths.get(args[1]), Paths.get(args[2]), limit, segSize, wikiBase);
            }
            case "segsearch" -> segSearch(
                    Paths.get(args[1]), Integer.parseInt(args[2]),
                    String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
            default -> System.err.println("unknown command: " + args[0]);
        }
    }

    static void index(Path dump, Path output, int limit, String wikiBase) throws Exception {
        Path titlesPath = output.resolveSibling(output.getFileName() + ".titles");
        Path bodiesPath = bodiesPath(output);
        Path bodiesOffPath = bodiesOffPath(output);
        writeWikiMeta(output, wikiBase);
        RamIndex.Builder builder = new RamIndex.Builder();
        long start = System.currentTimeMillis();
        int n = 0;
        Longs bodyOffsets = new Longs();
        bodyOffsets.add(0);
        try (DumpReader reader = new DumpReader(dump);
             BufferedWriter sidecar = Files.newBufferedWriter(titlesPath, StandardCharsets.UTF_8);
             OutputStream bodyOut = Files.newOutputStream(bodiesPath);
             DataOutputStream bodies = new DataOutputStream(bodyOut)) {
            Page page;
            while (n < limit && (page = reader.nextPage()) != null) {
                String indexed = page.title + " " + page.text;
                builder.addDocument(indexed);
                sidecar.write(safeForUtf8(page.title));
                sidecar.write('\t');
                sidecar.write(safeForUtf8(preview(page.text)));
                sidecar.newLine();
                writeBodyRecord(bodies, bodyOffsets, indexed);
                n++;
                if (n % 10_000 == 0) {
                    System.out.printf(Locale.ROOT, "indexed %d pages, %.1f s%n",
                            n, (System.currentTimeMillis() - start) / 1000.0);
                }
            }
        }
        writeBodyOffsets(bodiesOffPath, bodyOffsets.trimmed());
        System.out.printf(Locale.ROOT, "indexed %d pages, writing index...%n", n);
        DiskIndex.write(builder.build(), output);
        System.out.printf(Locale.ROOT, "wrote %s (%d bytes), %.1f s total%n",
                output, Files.size(output), (System.currentTimeMillis() - start) / 1000.0);
    }

    static void search(Path indexPath, int topK, String query) throws Exception {
        List<Doc> docs = loadDocs(indexPath);
        try (DiskIndex index = DiskIndex.open(indexPath)) {
            runQTree(new Finder(index), docs::get, query, topK, "");
        }
    }

    static void repl(Path indexPath, int topK) throws Exception {
        List<Doc> docs = loadDocs(indexPath);
        try (DiskIndex index = DiskIndex.open(indexPath)) {
            replLoop(new Finder(index), docs::get, topK, "");
        }
    }

    static void segRepl(Path dir, int topK) throws Exception {
        try (DocTable docs = DocTable.open(dir.resolve("titles.txt"));
             SegIndex index = SegIndex.open(dir)) {
            String extra = String.format(Locale.ROOT, ", %d segments", index.segmentCount());
            replLoop(new Finder(index), id -> {
                try {
                    return docs.get(id);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, topK, extra);
        }
    }

    private static void replLoop(Finder engine, IntFunction<Doc> docs,
                                 int topK, String statusExtra) throws Exception {
        System.out.printf(Locale.ROOT,
                "Interactive search (top %d). Operators: AND OR NOT ADJ[/n] NEAR[/n] ( ).%n",
                topK);
        System.out.println("Type a query and press Enter. Empty line or 'quit' to exit.");
        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                System.out.print("> ");
                System.out.flush();
                String query = in.readLine();
                if (query == null || query.isBlank()) {
                    break;
                }
                query = query.trim();
                if (query.equalsIgnoreCase("quit") || query.equalsIgnoreCase("exit") || query.equals("q")) {
                    break;
                }
                runQTree(engine, docs, query, topK, statusExtra);
            }
        }
    }

    private static void runQTree(Finder engine, IntFunction<Doc> docs,
                                 String query, int topK, String statusExtra) {
        long t0 = System.nanoTime();
        List<SearchResult> results = engine.search(query, topK);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf(Locale.ROOT, "%d hits, %d ms%s%n",
                results.size(), ms, statusExtra);
        for (SearchResult r : results) {
            Doc d = docs.apply(r.docId());
            System.out.printf(Locale.ROOT, "  %.4f  %s%n", r.score(), d.title);
            if (!d.preview.isEmpty()) {
                System.out.printf(Locale.ROOT, "          %s%n", d.preview);
            }
        }
    }

    static void ui(Path indexPath, String wikiBase) throws Exception {
        DocTable titles = DocTable.open(titlesPath(indexPath));
        BodyTable bodies = BodyTable.openOptional(bodiesPath(indexPath), bodiesOffPath(indexPath));
        DiskIndex index = DiskIndex.open(indexPath);
        showUi(new Finder(index), titles, bodies, "Wiki Search", "", wikiBase);
    }

    static void segUi(Path dir, String wikiBase) throws Exception {
        DocTable titles = DocTable.open(dir.resolve("titles.txt"));
        BodyTable bodies = BodyTable.openOptional(dir.resolve("bodies.bin"), dir.resolve("bodies.off"));
        SegIndex index = SegIndex.open(dir);
        showUi(new Finder(index), titles, bodies, "Wiki Search",
                String.format(Locale.ROOT, ", %d сегментов", index.segmentCount()), wikiBase);
    }

    private static void showUi(Finder engine, DocTable titles, BodyTable bodies,
                               String windowTitle, String statusExtra, String wikiBase) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame(windowTitle);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1400, 800);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    try {
                        titles.close();
                        if (bodies != null) {
                            bodies.close();
                        }
                    } catch (Exception ignored) {
                    }
                }
            });

            JTextField field = new JTextField();
            field.setToolTipText("AND  OR  NOT  ADJ[/n]  NEAR[/n]  ( )  — нужен явный AND между частями");
            JButton button = new JButton("Найти");
            JButton openBtn = new JButton("Wikipedia");
            openBtn.setToolTipText("Открыть статью в браузере");
            openBtn.setEnabled(false);
            JPanel top = new JPanel(new BorderLayout(8, 0));
            top.add(field, BorderLayout.CENTER);
            JPanel actions = new JPanel(new BorderLayout(4, 0));
            actions.add(openBtn, BorderLayout.WEST);
            actions.add(button, BorderLayout.EAST);
            top.add(actions, BorderLayout.EAST);

            DefaultListModel<Hit> model = new DefaultListModel<>();
            JList<Hit> list = new JList<>(model);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public java.awt.Component getListCellRendererComponent(
                        JList<?> parent, Object value, int index, boolean selected, boolean focused) {
                    JLabel label = (JLabel) super.getListCellRendererComponent(
                            parent, value, index, selected, focused);
                    if (value instanceof Hit hit) {
                        label.setText("<html><b>" + escape(hit.title()) + "</b>  "
                                + String.format(Locale.ROOT, "<font color='gray'>%.3f</font>", hit.score())
                                + "<br><font color='gray'>" + escape(hit.preview()) + "</font></html>");
                    }
                    return label;
                }
            });
            list.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));

            JTextPane bodyPane = new JTextPane();
            bodyPane.setContentType("text/html");
            bodyPane.setEditable(false);
            bodyPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            bodyPane.setText("<html><body style='font-family:monospace;font-size:12px;color:#666'>"
                    + "Выберите документ в списке</body></html>");

            JLabel status = new JLabel(" ");
            final String[] lastQuery = {""};

            Runnable showBody = () -> {
                Hit hit = list.getSelectedValue();
                if (hit == null) {
                    return;
                }
                try {
                    String body = bodies != null ? bodies.get(hit.docId()) : null;
                    if (body == null) {
                        body = hit.preview().isEmpty()
                                ? "(нет локального дампа — переиндексируйте с новой версией)"
                                : hit.preview();
                    }
                    Set<String> terms = queryTerms(lastQuery[0]);
                    bodyPane.setText(highlightBody(body, terms));
                    bodyPane.setCaretPosition(0);
                    status.setText(String.format(Locale.ROOT,
                            " docId=%d  score=%.3f  %s  %d симв.%s",
                            hit.docId(), hit.score(), hit.title(), body.length(), statusExtra));
                } catch (Exception ex) {
                    bodyPane.setText("<html><body>" + escape(ex.getMessage()) + "</body></html>");
                }
            };

            Runnable openSelected = () -> {
                Hit hit = list.getSelectedValue();
                if (hit == null) {
                    return;
                }
                openWikiPage(frame, wikiBase, hit.title());
            };

            Runnable doSearch = () -> {
                String query = field.getText().trim();
                if (query.isEmpty()) {
                    return;
                }
                lastQuery[0] = query;
                long t0 = System.nanoTime();
                List<SearchResult> results = engine.search(query, 50);
                long ms = (System.nanoTime() - t0) / 1_000_000;
                model.clear();
                for (SearchResult r : results) {
                    try {
                        Doc d = titles.get(r.docId());
                        model.addElement(new Hit(d.title, d.preview, r.score(), r.docId()));
                    } catch (Exception ex) {
                        model.addElement(new Hit("doc " + r.docId(), "", r.score(), r.docId()));
                    }
                }
                openBtn.setEnabled(!results.isEmpty());
                if (!results.isEmpty()) {
                    list.setSelectedIndex(0);
                } else {
                    bodyPane.setText("<html><body style='color:#666'>Ничего не найдено</body></html>");
                }
                status.setText(String.format(Locale.ROOT,
                        " найдено: %d, время: %d мс%s — выберите документ слева",
                        results.size(), ms, statusExtra));
            };

            list.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    openBtn.setEnabled(list.getSelectedValue() != null);
                    showBody.run();
                }
            });
            button.addActionListener(e -> doSearch.run());
            openBtn.addActionListener(e -> openSelected.run());
            field.addActionListener(e -> doSearch.run());

            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    new JScrollPane(list), new JScrollPane(bodyPane));
            split.setResizeWeight(0.35);
            split.setDividerLocation(420);

            frame.add(top, BorderLayout.NORTH);
            frame.add(split, BorderLayout.CENTER);
            frame.add(status, BorderLayout.SOUTH);
            frame.setVisible(true);
            field.requestFocusInWindow();
        });
    }

    private record Hit(String title, String preview, double score, int docId) {
    }

    private static void openWikiPage(java.awt.Component parent, String wikiBase, String title) {
        String url = wikiPageUrl(wikiBase, title);
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            } else {
                JOptionPane.showMessageDialog(parent,
                        "Откройте вручную:\n" + url, "Wikipedia", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parent,
                    "Не удалось открыть браузер.\n" + url,
                    "Wikipedia", JOptionPane.WARNING_MESSAGE);
        }
    }

    public static String wikiPageUrl(String wikiBase, String title) {
        String base = normalizeWikiBase(wikiBase);
        String article = title.trim().replace(' ', '_');
        byte[] utf8 = article.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder();
        for (byte raw : utf8) {
            int c = raw & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.'
                    || c == '(' || c == ')') {
                encoded.append((char) c);
            } else {
                encoded.append(String.format(Locale.ROOT, "%%%02X", c));
            }
        }
        return base + "/wiki/" + encoded;
    }

    private static String wikiBaseArg(String[] args, int limitIndex) {
        if (args.length > limitIndex && args[limitIndex].startsWith("http")) {
            return normalizeWikiBase(args[limitIndex]);
        }
        if (args.length > limitIndex + 1 && args[limitIndex + 1].startsWith("http")) {
            return normalizeWikiBase(args[limitIndex + 1]);
        }
        return DEFAULT_WIKI_BASE;
    }

    public static String normalizeWikiBase(String wikiBase) {
        String base = wikiBase.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base.isEmpty() ? DEFAULT_WIKI_BASE : base;
    }

    private static void writeWikiMeta(Path indexPath, String wikiBase) throws Exception {
        Path meta = metaPath(indexPath);
        Files.writeString(meta, "wiki.base=" + normalizeWikiBase(wikiBase) + "\n", StandardCharsets.UTF_8);
    }

    static String readWikiBase(Path indexOrDir) throws Exception {
        Path meta = Files.isDirectory(indexOrDir)
                ? indexOrDir.resolve("wiki.meta")
                : metaPath(indexOrDir);
        if (!Files.isRegularFile(meta)) {
            return DEFAULT_WIKI_BASE;
        }
        for (String line : Files.readAllLines(meta, StandardCharsets.UTF_8)) {
            if (line.startsWith("wiki.base=")) {
                return normalizeWikiBase(line.substring("wiki.base=".length()));
            }
        }
        return DEFAULT_WIKI_BASE;
    }

    private static Path metaPath(Path indexPath) {
        return indexPath.resolveSibling(indexPath.getFileName() + ".meta");
    }

    static void stats(Path indexPath, String[] terms) throws Exception {
        long fileSize = Files.size(indexPath);
        try (DiskIndex index = DiskIndex.open(indexPath)) {
            DiskIndex.Stats s = index.stats();
            long rawPostings = (long) s.totalPostings() * 8;
            long rawPositions = s.totalPositions() * 4;
            long rawDocLengths = (long) s.numDocs() * 4;
            long rawTotal = rawPostings + rawPositions + rawDocLengths;

            System.out.printf(Locale.ROOT, "file size:         %12s  (%.1f MB)%n",
                    fmt(fileSize), fileSize / 1024.0 / 1024);
            System.out.printf(Locale.ROOT, "documents:         %12s%n", fmt(s.numDocs()));
            System.out.printf(Locale.ROOT, "dictionary terms:  %12s%n", fmt(s.dictionarySize()));
            System.out.printf(Locale.ROOT, "total postings:    %12s  (raw %.1f MB)%n",
                    fmt(s.totalPostings()), rawPostings / 1024.0 / 1024);
            System.out.printf(Locale.ROOT, "total positions:   %12s  (raw %.1f MB)%n",
                    fmt(s.totalPositions()), rawPositions / 1024.0 / 1024);
            System.out.printf(Locale.ROOT, "raw int estimate:  %12s  (%.1f MB)%n",
                    fmt(rawTotal), rawTotal / 1024.0 / 1024);
            System.out.printf(Locale.ROOT, "compression ratio: %.2fx  (file vs raw int representation)%n",
                    (double) rawTotal / fileSize);

            if (terms.length > 0) {
                System.out.println();
                System.out.println("term frequencies:");
                for (String term : terms) {
                    int df = index.docFreq(term.toLowerCase(Locale.ROOT));
                    System.out.printf(Locale.ROOT, "  %-30s %12s docs%n", term, fmt(df));
                }
            }
        }
    }

    static void segIndex(Path dump, Path dir, int limit, int segSize, String wikiBase) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("wiki.meta"),
                "wiki.base=" + normalizeWikiBase(wikiBase) + "\n", StandardCharsets.UTF_8);
        Path bodiesPath = dir.resolve("bodies.bin");
        Path bodiesOffPath = dir.resolve("bodies.off");
        long start = System.currentTimeMillis();
        int globalDoc = 0;
        int segNum = 0;
        int inSeg = 0;
        RamIndex.Builder builder = new RamIndex.Builder();
        Longs bodyOffsets = new Longs();
        bodyOffsets.add(0);
        try (DumpReader reader = new DumpReader(dump);
             BufferedWriter titles = Files.newBufferedWriter(dir.resolve("titles.txt"), StandardCharsets.UTF_8);
             OutputStream bodyOut = Files.newOutputStream(bodiesPath);
             DataOutputStream bodies = new DataOutputStream(bodyOut)) {
            Page page;
            while (globalDoc < limit && (page = reader.nextPage()) != null) {
                String indexed = page.title + " " + page.text;
                builder.addDocument(indexed);
                titles.write(safeForUtf8(page.title));
                titles.write('\t');
                titles.write(safeForUtf8(preview(page.text)));
                titles.newLine();
                writeBodyRecord(bodies, bodyOffsets, indexed);
                globalDoc++;
                inSeg++;
                if (inSeg == segSize) {
                    flushSegment(builder, dir, segNum);
                    System.out.printf(Locale.ROOT, "  segment %d: %d docs, total %d, %.1f s%n",
                            segNum, inSeg, globalDoc, (System.currentTimeMillis() - start) / 1000.0);
                    builder = new RamIndex.Builder();
                    segNum++;
                    inSeg = 0;
                }
            }
            if (inSeg > 0) {
                flushSegment(builder, dir, segNum);
                segNum++;
            }
        }
        writeBodyOffsets(bodiesOffPath, bodyOffsets.trimmed());
        System.out.printf(Locale.ROOT, "done: %d docs in %d segments -> %s, %.1f s%n",
                globalDoc, segNum, dir, (System.currentTimeMillis() - start) / 1000.0);
    }

    private static void flushSegment(RamIndex.Builder builder, Path dir, int segNum) throws Exception {
        DiskIndex.write(builder.build(),
                dir.resolve(String.format(Locale.ROOT, "segment-%05d.idx", segNum)));
    }

    static void segSearch(Path dir, int topK, String query) throws Exception {
        try (DocTable docs = DocTable.open(dir.resolve("titles.txt"));
             SegIndex index = SegIndex.open(dir)) {
            String extra = String.format(Locale.ROOT, ", %d segments", index.segmentCount());
            System.out.printf(Locale.ROOT, "query: %s%n", query);
            runQTree(new Finder(index), id -> {
                try {
                    return docs.get(id);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, query, topK, extra);
        }
    }

    private static String fmt(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static String preview(String text) {
        String clean = text
                .replaceAll("\\{\\{[^{}]*\\}\\}", " ")
                .replaceAll("<[^>]*>", " ")
                .replaceAll("'+|\\[\\[|\\]\\]|=+", " ")
                .replaceAll("\\s+", " ")
                .replace('\t', ' ')
                .trim();
        if (clean.length() <= PREVIEW_LEN) {
            return clean;
        }
        return clean.substring(0, PREVIEW_LEN) + "…";
    }

    private static String safeForUtf8(String text) {
        StringBuilder out = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                    if (out != null) {
                        out.append(c).append(text.charAt(i + 1));
                    }
                    i++;
                } else {
                    if (out == null) {
                        out = new StringBuilder(text.length());
                        out.append(text, 0, i);
                    }
                    out.append('?');
                }
            } else if (Character.isLowSurrogate(c)) {
                if (out == null) {
                    out = new StringBuilder(text.length());
                    out.append(text, 0, i);
                }
                out.append('?');
            } else if (out != null) {
                out.append(c);
            }
        }
        return out == null ? text : out.toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static Path titlesPath(Path indexPath) {
        return indexPath.resolveSibling(indexPath.getFileName() + ".titles");
    }

    private static Path bodiesPath(Path indexPath) {
        return indexPath.resolveSibling(indexPath.getFileName() + ".bodies");
    }

    private static Path bodiesOffPath(Path indexPath) {
        return indexPath.resolveSibling(indexPath.getFileName() + ".bodies.off");
    }

    private static void writeBodyRecord(DataOutputStream out, Longs offsets, String text) throws Exception {
        byte[] bytes = safeForUtf8(text).getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
        offsets.add(offsets.a[offsets.size - 1] + 4L + bytes.length);
    }

    private static void writeBodyOffsets(Path offPath, long[] offsets) throws Exception {
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(offPath))) {
            out.writeInt(offsets.length);
            for (long offset : offsets) {
                out.writeLong(offset);
            }
        }
    }

    private static Set<String> queryTerms(String query) {
        if (query == null || query.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(QParse.terms(QParse.parse(query)));
    }

    private static String highlightBody(String text, Set<String> terms) {
        Set<String> lower = new LinkedHashSet<>();
        for (String term : terms) {
            if (term != null && !term.isEmpty()) {
                lower.add(term.toLowerCase(Locale.ROOT));
            }
        }
        StringBuilder html = new StringBuilder("<html><body style='font-family:monospace;font-size:12px'>");
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                int start = i;
                while (i < text.length() && Character.isLetterOrDigit(text.charAt(i))) {
                    i++;
                }
                String token = text.substring(start, i);
                if (lower.contains(token.toLowerCase(Locale.ROOT))) {
                    html.append("<span style='background:#fff59d;font-weight:bold'>")
                            .append(escape(token)).append("</span>");
                } else {
                    html.append(escape(token));
                }
            } else {
                html.append(escape(String.valueOf(c)));
                i++;
            }
        }
        html.append("</body></html>");
        return html.toString();
    }

    private static List<Doc> loadDocs(Path indexPath) throws Exception {
        return parseDocs(titlesPath(indexPath));
    }

    private static List<Doc> parseDocs(Path titlesPath) throws Exception {
        List<String> lines = Files.readAllLines(titlesPath, StandardCharsets.UTF_8);
        List<Doc> docs = new ArrayList<>(lines.size());
        for (String line : lines) {
            int tab = line.indexOf('\t');
            docs.add(tab < 0
                    ? new Doc(line, "")
                    : new Doc(line.substring(0, tab), line.substring(tab + 1)));
        }
        return docs;
    }

    private record Doc(String title, String preview) {
    }

    private static final class DocTable implements AutoCloseable {
        private final FileChannel channel;
        private final long[] offsets;
        private final long fileSize;

        private DocTable(FileChannel channel, long[] offsets, long fileSize) {
            this.channel = channel;
            this.offsets = offsets;
            this.fileSize = fileSize;
        }

        static DocTable open(Path path) throws Exception {
            Longs offsets = new Longs();
            offsets.add(0);
            long pos = 0;
            try (InputStream in = new BufferedInputStream(Files.newInputStream(path), 1 << 20)) {
                byte[] buffer = new byte[1 << 20];
                int n;
                while ((n = in.read(buffer)) >= 0) {
                    for (int i = 0; i < n; i++) {
                        pos++;
                        if (buffer[i] == '\n') {
                            offsets.add(pos);
                        }
                    }
                }
            }
            if (offsets.size > 0 && offsets.a[offsets.size - 1] == pos) {
                offsets.size--;
            }
            return new DocTable(FileChannel.open(path, StandardOpenOption.READ), offsets.trimmed(), pos);
        }

        Doc get(int docId) throws Exception {
            if (docId < 0 || docId >= offsets.length) {
                return new Doc("doc " + docId, "");
            }
            long start = offsets[docId];
            long end = docId + 1 < offsets.length ? offsets[docId + 1] : fileSize;
            while (end > start && isLineBreak(byteAt(end - 1))) {
                end--;
            }
            ByteBuffer buffer = ByteBuffer.allocate((int) (end - start));
            long pos = start;
            while (buffer.hasRemaining()) {
                pos += channel.read(buffer, pos);
            }
            buffer.flip();
            String line = StandardCharsets.UTF_8.decode(buffer).toString();
            int tab = line.indexOf('\t');
            return tab < 0
                    ? new Doc(line, "")
                    : new Doc(line.substring(0, tab), line.substring(tab + 1));
        }

        private byte byteAt(long pos) throws Exception {
            ByteBuffer one = ByteBuffer.allocate(1);
            channel.read(one, pos);
            return one.array()[0];
        }

        private static boolean isLineBreak(byte b) {
            return b == '\n' || b == '\r';
        }

        @Override
        public void close() throws Exception {
            channel.close();
        }
    }

    private static final class BodyTable implements AutoCloseable {
        private final FileChannel channel;
        private final long[] offsets;

        private BodyTable(FileChannel channel, long[] offsets) {
            this.channel = channel;
            this.offsets = offsets;
        }

        static BodyTable openOptional(Path bodiesPath, Path offPath) throws Exception {
            if (!Files.isRegularFile(bodiesPath) || !Files.isRegularFile(offPath)) {
                return null;
            }
            return open(bodiesPath, offPath);
        }

        static BodyTable open(Path bodiesPath, Path offPath) throws Exception {
            try (DataInputStream in = new DataInputStream(
                    new BufferedInputStream(Files.newInputStream(offPath)))) {
                int count = in.readInt();
                long[] offsets = new long[count];
                for (int i = 0; i < count; i++) {
                    offsets[i] = in.readLong();
                }
                return new BodyTable(FileChannel.open(bodiesPath, StandardOpenOption.READ), offsets);
            }
        }

        String get(int docId) throws Exception {
            if (docId < 0 || docId + 1 >= offsets.length) {
                return "";
            }
            long start = offsets[docId];
            long end = offsets[docId + 1];
            int recordLen = (int) (end - start);
            ByteBuffer buffer = ByteBuffer.allocate(recordLen);
            long pos = start;
            while (buffer.hasRemaining()) {
                pos += channel.read(buffer, pos);
            }
            buffer.flip();
            int len = buffer.getInt();
            byte[] bytes = new byte[len];
            buffer.get(bytes);
            return StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes)).toString();
        }

        @Override
        public void close() throws Exception {
            channel.close();
        }
    }

    private static final class Longs {
        private long[] a = new long[1024];
        private int size;

        void add(long value) {
            if (size == a.length) {
                a = Arrays.copyOf(a, a.length * 2);
            }
            a[size++] = value;
        }

        long[] trimmed() {
            return Arrays.copyOf(a, size);
        }
    }

    private record Page(String title, String text) {
    }

    private static final class DumpReader implements AutoCloseable {
        private final InputStream in;
        private final XMLStreamReader xml;

        DumpReader(Path path) throws Exception {
            in = new BufferedInputStream(Files.newInputStream(path), 1 << 20);
            XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            for (String key : XML_SIZE_LIMITS) {
                setIfSupported(factory, key, "0");
            }
            setIfSupported(factory, FEATURE_SECURE_PROCESSING, false);
            setIfSupported(factory, ACCESS_EXTERNAL_DTD, "");
            setIfSupported(factory, ACCESS_EXTERNAL_SCHEMA, "");
            xml = factory.createXMLStreamReader(in, "UTF-8");
        }

        Page nextPage() throws Exception {
            while (xml.hasNext()) {
                if (xml.next() != START_ELEMENT || !"page".equals(xml.getLocalName())) {
                    continue;
                }
                String title = null;
                String text = null;
                int ns = 0;
                boolean redirect = false;
                while (true) {
                    int e = xml.next();
                    if (e == END_ELEMENT && "page".equals(xml.getLocalName())) {
                        break;
                    }
                    if (e != START_ELEMENT) {
                        continue;
                    }
                    String name = xml.getLocalName();
                    if ("title".equals(name)) {
                        title = xml.getElementText();
                    } else if ("ns".equals(name)) {
                        ns = Integer.parseInt(xml.getElementText());
                    } else if ("redirect".equals(name)) {
                        redirect = true;
                    } else if ("text".equals(name)) {
                        text = xml.getElementText();
                    }
                }
                if (ns == 0 && !redirect && text != null) {
                    return new Page(title, text);
                }
            }
            return null;
        }

        @Override
        public void close() throws Exception {
            xml.close();
            in.close();
        }
    }

    private static void setIfSupported(XMLInputFactory factory, String name, Object value) {
        if (factory.isPropertySupported(name)) {
            factory.setProperty(name, value);
        }
    }
}
