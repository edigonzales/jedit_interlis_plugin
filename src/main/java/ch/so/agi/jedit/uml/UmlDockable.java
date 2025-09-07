package ch.so.agi.jedit.uml;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.Log;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;

import ch.interlis.ili2c.metamodel.*;
import ch.so.agi.jedit.compile.TdCache;

import org.jhotdraw.draw.DefaultDrawing;
import org.jhotdraw.draw.DefaultDrawingEditor;
import org.jhotdraw.draw.DefaultDrawingView;
import org.jhotdraw.draw.Drawing;
import org.jhotdraw.draw.DrawingEditor;
import org.jhotdraw.draw.Figure;

public final class UmlDockable extends JPanel {

    private static final String PREF_REMEMBER_POS = "interlis.uml.rememberPositions";
    private static final String TAB_KEY = "uml.tab.key";

    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 4.0;
    private static final double WHEEL_STEP = 1.1;

    private final View view;
    private final String position;

    private final JTabbedPane tabs = new JTabbedPane();
    private final JButton refreshBtn = new JButton("Refresh");

    private final JButton zoomOutBtn  = new JButton("–");
    private final JButton zoomResetBtn= new JButton("100%");
    private final JButton zoomInBtn   = new JButton("+");

    private final JButton menuBtn = new JButton("⋮");
    private final JPopupMenu tbMenu = buildToolbarMenu();

    private static boolean showAssociations = true;

    private final Map<String, Point2D.Double> rememberedPos = new HashMap<>();
    private boolean rememberPositions = jEdit.getBooleanProperty(PREF_REMEMBER_POS, true);
    private final Map<String, Color> rememberedFills = new HashMap<>();

    public UmlDockable(View view, String position) {
        super(new BorderLayout(8, 8));
        this.view = view;
        this.position = position;

        showAssociations = jEdit.getBooleanProperty("interlis.uml.showAssociations", true);

        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.setRollover(true);

        compact(refreshBtn);
        tb.add(refreshBtn);

        tb.add(Box.createHorizontalStrut(8));
        tb.add(thinSep(6));
        tb.add(Box.createHorizontalStrut(4));

        compact(zoomOutBtn);
        compact(zoomResetBtn);
        compact(zoomInBtn);
        tb.add(zoomOutBtn);
        tb.add(Box.createHorizontalStrut(4));
        tb.add(zoomResetBtn);
        tb.add(Box.createHorizontalStrut(4));
        tb.add(zoomInBtn);

        menuBtn.setFocusable(false);
        menuBtn.setBorderPainted(false);
        menuBtn.setOpaque(false);
        tb.addSeparator();
        tb.add(menuBtn);
        menuBtn.addActionListener(e -> tbMenu.show(menuBtn, 0, menuBtn.getHeight()));

        tb.add(Box.createHorizontalGlue());
        add(tb, BorderLayout.NORTH);

        zoomOutBtn.addActionListener(e -> doZoomOut());
        zoomResetBtn.addActionListener(e -> doZoomReset());
        zoomInBtn.addActionListener(e -> doZoomIn());

        add(tb, BorderLayout.NORTH);

        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        add(tabs, BorderLayout.CENTER);

        refreshBtn.addActionListener(e -> rebuildFromCache());

        rebuildFromCache();
    }

    private static void compact(JButton b) {
        b.setFocusable(false);
        b.setMargin(new Insets(2, 8, 2, 8));
    }

    private static JToolBar.Separator thinSep(int px) {
        JToolBar.Separator s = new JToolBar.Separator(new Dimension(px, 0));
        s.setMaximumSize(new Dimension(px, Integer.MAX_VALUE));
        return s;
    }

    private JPopupMenu buildToolbarMenu() {
        JPopupMenu m = new JPopupMenu();

        JCheckBoxMenuItem miRemember =
                new JCheckBoxMenuItem("Remember positions", rememberPositions);
        miRemember.addActionListener(e -> {
            rememberPositions = miRemember.isSelected();
            jEdit.setBooleanProperty(PREF_REMEMBER_POS, rememberPositions);
            if (!rememberPositions) {
                rememberedPos.clear();
            } else {
                snapshotPositionsFromTabs();
            }
            rebuildFromCache();
        });
        m.add(miRemember);

        JCheckBoxMenuItem miShowAssoc = new JCheckBoxMenuItem("Show associations", showAssociations);
        miShowAssoc.addActionListener(e -> {
            showAssociations = miShowAssoc.isSelected();
            jEdit.setBooleanProperty("interlis.uml.showAssociations", showAssociations);
            rebuildFromCache();
        });
        m.add(miShowAssoc);

        m.addSeparator();
        JMenu export = new JMenu("Export PNG");
        JMenuItem expActive = new JMenuItem("Export active tab");
        JMenuItem expAll    = new JMenuItem("Export all tabs");
        expActive.addActionListener(e -> exportActiveTabAsPng());
        expAll.addActionListener(e -> exportAllTabsAsPng());
        export.add(expActive);
        export.add(expAll);
        m.add(export);

        m.addSeparator();

        JMenuItem saveUml = new JMenuItem("Save UML");
        saveUml.addActionListener(e -> saveUmlToFile());
        m.add(saveUml);

        return m;
    }

    /** Rebuild tabs from the latest cached TD. */
    private void rebuildFromCache() {
        if (rememberPositions) snapshotPositionsFromTabs();
        maybeLoadUmlFromFile();

        Object prevKey = null;
        int prevIndex  = tabs.getSelectedIndex();
        if (prevIndex >= 0 && prevIndex < tabs.getTabCount()) {
            Component c = tabs.getComponentAt(prevIndex);
            if (c instanceof JComponent) prevKey = ((JComponent) c).getClientProperty(TAB_KEY);
        }

        Buffer buf = view.getBuffer();
        TransferDescription td = TdCache.peekLastValid(buf);
        if (td == null) {
            tabs.removeAll();
            tabs.addTab("No data", msgPanel(
                    "No compiled model found.\n" +
                    "Run “Compile current file” (or save) to populate the UML."));
            return;
        }

        Set<String> liveKeys = new HashSet<>();

        tabs.removeAll();
        Model[] models = td.getModelsFromLastFile();
        if (models == null || models.length == 0) {
            tabs.addTab("No data", msgPanel("No models in last file."));
            return;
        }

        int indexToSelect = -1;

        final Map<String, Point2D.Double> posCache = rememberPositions ? rememberedPos : null;

        for (Model model : models) {
            List<Topic> topics = collectTopics(model);
            List<Viewable> modelViewables = collectModelLevelViewables(model); // NEW

            for (Viewable v : modelViewables) {
                String k = keyFor(v);
                if (k != null) liveKeys.add(k);
            }

            if (topics.isEmpty() && modelViewables.isEmpty()) {
                tabs.addTab(model.getName(), msgPanel("No topics or model-level viewables."));
            } else {
                JComponent overview = canvasForOverview(topics, modelViewables, posCache);
                String keyOverview  = "model:" + model.getName() + ":overview";
                ((JComponent) overview).putClientProperty(TAB_KEY, keyOverview);
                tabs.addTab(model.getName(), overview);
                if (prevKey != null && prevKey.equals(keyOverview)) {
                    indexToSelect = tabs.indexOfComponent(overview);
                }
            }

            for (Topic topic : topics) {
                List<Viewable> topicViewables = collectTopicViewables(topic); // NEW

                for (Viewable v : topicViewables) {
                    String k = keyFor(v);
                    if (k != null) liveKeys.add(k);
                }

                String tabTitle = topic.getName();

                if (topicViewables.isEmpty()) {
                    tabs.addTab(tabTitle, msgPanel("No viewables in this topic."));
                } else {
                    JComponent topicCanvas = canvasFor(topicViewables, model, posCache);
                    String keyTopic = "topic:" + model.getName() + "::" + topic.getName();
                    ((JComponent) topicCanvas).putClientProperty(TAB_KEY, keyTopic);
                    tabs.addTab(topic.getName(), topicCanvas);
                    if (prevKey != null && prevKey.equals(keyTopic)) {
                        indexToSelect = tabs.indexOfComponent(topicCanvas);
                    }
                }
            }
        }

        if (indexToSelect >= 0) {
            tabs.setSelectedIndex(indexToSelect);
        } else if (prevIndex >= 0 && prevIndex < tabs.getTabCount()) {
            tabs.setSelectedIndex(prevIndex);
        } else if (tabs.getTabCount() > 0) {
            tabs.setSelectedIndex(0);
        }

        if (rememberPositions) pruneRememberedTo(liveKeys);

        tabs.revalidate();
        tabs.repaint();
    }

    private void pruneRememberedTo(Collection<String> liveKeys) {
        rememberedPos.keySet().retainAll(liveKeys);
        rememberedFills.keySet().retainAll(liveKeys);
    }

    private File umlFileForBuffer() {
        Buffer b = view.getBuffer();
        if (b == null) return null;
        String dir = b.getDirectory();
        String name = b.getName();
        String base = name.replaceFirst("\\.[^.]*$", "");
        return new File(dir, base + ".uml");
    }

    private static String colorToHex(Color c) {
        if (c == null) return null;
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static Color hexToColor(String hex) {
        if (hex == null || hex.isEmpty()) return null;
        try { return Color.decode(hex); } catch (Exception e) { return null; }
    }

    private void saveUmlToFile() {
        try {
            snapshotStateFromTabs();

            File out = umlFileForBuffer();
            if (out == null) { Toolkit.getDefaultToolkit().beep(); return; }

            StringBuilder sb = new StringBuilder(16_384);
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<uml version=\"1\">\n");
            sb.append("  <options showAssociations=\"").append(showAssociations).append("\"/>\n");
            sb.append("  <classes>\n");
            for (Map.Entry<String, Point2D.Double> e : rememberedPos.entrySet()) {
                String scoped = e.getKey();
                Point2D.Double p = e.getValue();
                Color fill = rememberedFills.get(scoped);
                sb.append("    <class scoped=\"").append(escapeXml(scoped)).append("\" ")
                  .append("x=\"").append(p.x).append("\" ")
                  .append("y=\"").append(p.y).append("\"");
                if (fill != null) sb.append(" fill=\"").append(colorToHex(fill)).append("\"");
                sb.append("/>\n");
            }
            sb.append("  </classes>\n");
            sb.append("</uml>\n");

            File tmp = File.createTempFile("uml_", ".xml", out.getParentFile());
            try (java.io.OutputStream os = new java.io.BufferedOutputStream(new java.io.FileOutputStream(tmp))) {
                os.write(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(out)) {
                try (java.io.InputStream in = new java.io.FileInputStream(tmp);
                     java.io.OutputStream outS = new java.io.FileOutputStream(out)) {
                    in.transferTo(outS);
                }
                tmp.delete();
            }
            JOptionPane.showMessageDialog(this,
                    "Saved UML: " + out.getAbsolutePath(),
                    "Save UML", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to save UML: " + ex.getMessage(),
                    "Save UML", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void maybeLoadUmlFromFile() {
        if (!rememberedPos.isEmpty() || !rememberedFills.isEmpty()) return;

        File f = umlFileForBuffer();
        if (f == null || !f.isFile()) return;

        try {
            org.w3c.dom.Document doc = javax.xml.parsers.DocumentBuilderFactory
                    .newInstance().newDocumentBuilder().parse(f);
            doc.getDocumentElement().normalize();

            org.w3c.dom.NodeList opts = doc.getElementsByTagName("options");
            if (opts.getLength() > 0) {
                org.w3c.dom.Element e = (org.w3c.dom.Element) opts.item(0);
                String sa = e.getAttribute("showAssociations");
                if (!sa.isEmpty()) {
                    showAssociations = Boolean.parseBoolean(sa);
                    jEdit.setBooleanProperty("interlis.uml.showAssociations", showAssociations);
                }
            }

            org.w3c.dom.NodeList cls = doc.getElementsByTagName("class");
            for (int i = 0; i < cls.getLength(); i++) {
                org.w3c.dom.Element e = (org.w3c.dom.Element) cls.item(i);
                String scoped = e.getAttribute("scoped");
                if (scoped == null || scoped.isEmpty()) continue;
                double x = Double.parseDouble(e.getAttribute("x"));
                double y = Double.parseDouble(e.getAttribute("y"));
                rememberedPos.put(scoped, new Point2D.Double(x, y));
                String fill = e.getAttribute("fill");
                if (fill != null && !fill.isEmpty()) {
                    Color c = hexToColor(fill);
                    if (c != null) rememberedFills.put(scoped, c);
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to load UML: " + ex.getMessage(),
                    "Save UML", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                .replace("\"","&quot;").replace("'","&apos;");
    }

    private void snapshotStateFromTabs() {
        snapshotPositionsFromTabs();
        snapshotFillsFromTabs();
    }

    private void snapshotFillsFromTabs() {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Component comp = tabs.getComponentAt(i);
            DefaultDrawingView dv = findDrawingView(comp);
            if (dv == null) continue;

            Drawing drawing = dv.getDrawing();
            for (Figure f : new ArrayList<>(drawing.getChildren())) {
                if (f instanceof ClassFigure) {
                    ClassFigure cf = (ClassFigure) f;
                    Viewable owner = cf.getOwnerViewable(); // UPDATED
                    if (owner != null) {
                        String key = keyFor(owner);
                        if (key != null && cf.getBackgroundColor() != null) {
                            rememberedFills.put(key, cf.getBackgroundColor());
                        }
                    }
                }
            }
        }
    }

    /* ---- Export API ---- */

    private void exportActiveTabAsPng() {
        File dir = currentBufferDir();
        if (dir == null) {
            JOptionPane.showMessageDialog(this,
                "Please save the ILI file first.\nI need its directory for export.",
                "Export PNG", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JScrollPane sp = currentScroller();
        DefaultDrawingView view = currentView(sp);
        if (view == null) { Toolkit.getDefaultToolkit().beep(); return; }

        String base = fileBaseForTab(tabs.getSelectedComponent());
        File out = new File(dir, base + ".png");
        try {
            exportViewToPNG(view, out, 2.0);
            JOptionPane.showMessageDialog(this, "Exported: " + out.getAbsolutePath(),
                    "Export PNG", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to export:\n" + ex.getMessage(),
                    "Export PNG", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportAllTabsAsPng() {
        File dir = currentBufferDir();
        if (dir == null) {
            JOptionPane.showMessageDialog(this,
                "Please save the ILI file first.\nI need its directory for export.",
                "Export PNG", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int ok = 0, skipped = 0, failed = 0;
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Component comp = tabs.getComponentAt(i);
            DefaultDrawingView dv = findDrawingView(comp);
            if (dv == null) { skipped++; continue; }

            String base = fileBaseForTab(comp);
            File out = new File(dir, base + ".png");
            try {
                exportViewToPNG(dv, out, 2.0);
                ok++;
            } catch (Exception ex) { failed++; }
        }
        JOptionPane.showMessageDialog(this,
            "PNG export finished.\nSaved: " + ok + (skipped>0?("\nSkipped (no diagram): "+skipped):"")
            + (failed>0?("\nFailed: "+failed):""),
            "Export PNG", JOptionPane.INFORMATION_MESSAGE);
    }

    private static void exportViewToPNG(DefaultDrawingView view, File outFile, double scale) throws Exception {
        Drawing drawing = view.getDrawing();

        for (Figure f : new ArrayList<>(drawing.getChildren())) {
            if (f instanceof org.jhotdraw.draw.ConnectionFigure) {
                ((org.jhotdraw.draw.ConnectionFigure) f).updateConnection();
            }
        }

        Rectangle2D union = computeUnionBounds(drawing);
        if (union == null || union.isEmpty()) {
            throw new IllegalStateException("Nothing to export (no visible bounds).");
        }

        int w = Math.max(1, (int) Math.ceil(union.getWidth()  * scale));
        int h = Math.max(1, (int) Math.ceil(union.getHeight() * scale));
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, w, h);

            g2.scale(scale, scale);
            g2.translate(-union.getX(), -union.getY());

            for (Figure f : new ArrayList<>(drawing.getChildren())) {
                f.draw(g2);
            }
        } finally {
            g2.dispose();
        }
        ImageIO.write(img, "png", outFile);
    }

    private static Rectangle2D safeDrawingArea(Figure f) {
        try {
            Rectangle2D a = f.getDrawingArea();
            if (a != null && !a.isEmpty()) return (Rectangle2D) a.clone();
        } catch (Throwable ignore) {}

        try {
            Rectangle2D b = f.getBounds();
            if (b != null && !b.isEmpty()) return (Rectangle2D) b.clone();

            if (f instanceof org.jhotdraw.draw.BezierFigure) {
                org.jhotdraw.geom.BezierPath p = ((org.jhotdraw.draw.BezierFigure) f).getBezierPath();
                if (p != null) {
                    Rectangle2D pb = p.getBounds2D();
                    if (pb != null && !pb.isEmpty()) return (Rectangle2D) pb.clone();
                }
            }
        } catch (Throwable ignore) {}
        return null;
    }

    private static Rectangle2D computeUnionBounds(Drawing d) {
        Rectangle2D union = null;
        for (Figure f : new ArrayList<>(d.getChildren())) {
            Rectangle2D r = safeDrawingArea(f);
            if (r == null) continue;
            union = (union == null) ? (Rectangle2D) r.clone() : union.createUnion(r);
        }
        return union;
    }

    /* ---- Naming & directory helpers ---- */

    private File currentBufferDir() {
        Buffer buf = view.getBuffer();
        String dir = (buf != null) ? buf.getDirectory() : null;
        if (dir == null) return null;
        return new File(dir);
    }

    private String fileBaseForTab(Component tabComp) {
        if (!(tabComp instanceof JComponent)) {
            int idx = tabs.indexOfComponent(tabComp);
            String title = (idx >= 0) ? tabs.getTitleAt(idx) : "diagram";
            return sanitizeBase(title);
        }
        Object keyObj = ((JComponent) tabComp).getClientProperty(TAB_KEY);
        if (!(keyObj instanceof String)) {
            int idx = tabs.indexOfComponent(tabComp);
            String title = (idx >= 0) ? tabs.getTitleAt(idx) : "diagram";
            return sanitizeBase(title);
        }
        String key = (String) keyObj;
        try {
            if (key.startsWith("topic:")) {
                String[] parts = key.substring("topic:".length()).split("::");
                if (parts.length == 2) return sanitizeBase(parts[0] + "_" + parts[1]);
            } else if (key.startsWith("model:")) {
                String rest = key.substring("model:".length());
                String model = rest;
                int c = rest.indexOf(':');
                if (c >= 0) model = rest.substring(0, c);
                return sanitizeBase(model + "_overview");
            }
        } catch (Exception ignored) {}
        int idx = tabs.indexOfComponent(tabComp);
        String title = (idx >= 0) ? tabs.getTitleAt(idx) : "diagram";
        return sanitizeBase(title);
    }

    private static String sanitizeBase(String s) {
        String base = s.trim().replaceAll("\\s+", "_");
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.isEmpty()) base = "diagram";
        return base;
    }

    /* ======================== model traversal helpers ======================== */

    private static List<Topic> collectTopics(Model model) {
        ArrayList<Topic> out = new ArrayList<>();
        for (Iterator<?> it = model.iterator(); it.hasNext();) {
            Object o = it.next();
            if (o instanceof Topic) out.add((Topic) o);
        }
        return out;
    }

    /** Viewables declared directly in the model (exclude Topic & AssociationDef). */
    private static List<Viewable> collectModelLevelViewables(Model model) {
        ArrayList<Viewable> out = new ArrayList<>();
        for (Iterator<?> it = model.iterator(); it.hasNext();) {
            Object o = it.next();
            if (o instanceof Topic) continue;
            if (o instanceof AssociationDef) continue; // edges only
            if (o instanceof Viewable) out.add((Viewable) o); // Tables + Views
        }
        return out;
    }

    /** Viewables declared inside a topic (exclude AssociationDef). */
    private static List<Viewable> collectTopicViewables(Topic topic) {
        ArrayList<Viewable> out = new ArrayList<>();
        for (Iterator<?> it = topic.iterator(); it.hasNext();) {
            Object o = it.next();
            if (o instanceof AssociationDef) continue; // edges only
            if (o instanceof Viewable) out.add((Viewable) o); // Tables + Views
        }
        return out;
    }

    /* ============================== canvases ================================= */

    /** Overview canvas: topics + model-level viewables in one grid. */
    private JComponent canvasForOverview(List<Topic> topics, List<Viewable> viewables,
                                         Map<String, Point2D.Double> posCache) {
        DefaultDrawingView drawingView = new DefaultDrawingView();
        drawingView.setBackground(Color.white);

        DrawingEditor editor = new DefaultDrawingEditor();
        editor.add(drawingView);
        editor.setActiveView(drawingView);

        Drawing drawing = new DefaultDrawing();
        drawingView.setDrawing(drawing);

        installClassColorUI(drawingView);

        Map<String, ClassFigure> figureByScoped = new HashMap<>();

        int total = topics.size() + viewables.size();
        if (total == 0) return new JScrollPane(drawingView);

        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(total)));
        int cellW = 320;
        int cellH = 200;
        int gap   = 30;

        int i = 0;
        Map<Topic, TopicFigure> byTopic = new HashMap<>();

        for (Topic t : topics) {
            int row = i / cols, col = i % cols;
            int x = gap + col * (cellW + gap);
            int y = gap + row * (cellH + gap);
            TopicFigure tf = addTopicFigure(drawing, t, x, y);
            byTopic.put(t, tf);
            i++;
        }

        addTopicDependencyEdges(drawing, topics, byTopic);

        for (Viewable v : viewables) {
            int row = i / cols, col = i % cols;
            int x = gap + col * (cellW + gap);
            int y = gap + row * (cellH + gap);
            ClassFigure cf = addViewableFigure(drawing, v, x, y);

            if (posCache != null) {
                String key = keyFor(v);
                Point2D.Double p = (key != null) ? posCache.get(key) : null;
                if (p != null) {
                    Rectangle2D nb = cf.getBounds();
                    cf.setBounds(new Point2D.Double(p.x, p.y),
                                 new Point2D.Double(p.x + nb.getWidth(), p.y + nb.getHeight()));
                }
            }

            figureByScoped.put(v.getScopedName(null), cf);
            i++;
        }

        // inheritance (direct)
        for (Viewable sub : viewables) {
            Element ext = (sub instanceof Extendable) ? ((Extendable) sub).getExtending() : null;
            if (ext instanceof Viewable) {
                Viewable sup = (Viewable) ext;

                ClassFigure subFig = figureByScoped.get(sub.getScopedName(null));
                ClassFigure supFig = figureByScoped.get(sup.getScopedName(null));

                if (subFig != null && supFig != null) {
                    GeneralizationFigure g = new GeneralizationFigure();
                    drawing.add(g);
                    g.setStartConnector(subFig.connector());
                    g.setEndConnector(supFig.connector());
                    g.updateConnection();
                }
            }
        }

        JScrollPane sp = new JScrollPane(drawingView);
        installWheelZoom(drawingView, sp);
        installPanSupport(drawingView, editor, sp);
        return sp;
    }

    private static void addTopicDependencyEdges(Drawing drawing, List<Topic> topics,
                                                Map<Topic, TopicFigure> byTopic) {
        for (Topic t : topics) {
            for (Topic dep : topicDependsOn(t)) {
                TopicFigure src = byTopic.get(t);
                TopicFigure dst = byTopic.get(dep);
                if (src == null || dst == null) continue;
                DependsConnectionFigure cf = new DependsConnectionFigure();
                drawing.add(cf);
                cf.setStartConnector(src.connector());
                cf.setEndConnector(dst.connector());
            }
        }
    }

    private static List<Topic> topicDependsOn(Topic t) {
        List<Topic> out = new ArrayList<>();
        if (t == null) return out;
        Iterator<Topic> it = t.getDependentOn();
        if (it == null) return out;
        while (it.hasNext()) {
            Topic dep = it.next();
            if (dep != t) out.add(dep);
        }
        return out;
    }

    /** Topic canvas with viewables (Tables + Views). */
    private JComponent canvasFor(List<Viewable> viewables, Model model,
                                 Map<String, Point2D.Double> posCache) {
        DefaultDrawingView drawingView = new DefaultDrawingView();
        drawingView.setBackground(Color.white);

        DrawingEditor editor = new DefaultDrawingEditor();
        editor.add(drawingView);
        editor.setActiveView(drawingView);

        Drawing drawing = new DefaultDrawing();
        drawingView.setDrawing(drawing);

        installClassColorUI(drawingView);

        Map<String, ClassFigure> figureByScoped = new HashMap<>();

        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(viewables.size())));
        int cellW = 280, cellH = 180, gap = 30;

        int i = 0;
        for (Viewable v : viewables) {
            int row = i / cols, col = i % cols;
            int x = gap + col * (cellW + gap);
            int y = gap + row * (cellH + gap);

            ClassFigure cf = addViewableFigure(drawing, v, x, y);

            if (posCache != null) {
                String key = keyFor(v);
                Point2D.Double p = (key != null) ? posCache.get(key) : null;
                if (p != null) {
                    Rectangle2D nb = cf.getBounds();
                    cf.setBounds(new Point2D.Double(p.x, p.y),
                                 new Point2D.Double(p.x + nb.getWidth(), p.y + nb.getHeight()));
                }
            }

            figureByScoped.put(v.getScopedName(null), cf);
            i++;
        }

        // inheritance (direct)
        for (Viewable sub : viewables) {
            Element ext = (sub instanceof Extendable) ? ((Extendable) sub).getExtending() : null;
            if (ext instanceof Viewable) {
                Viewable sup = (Viewable) ext;
                ClassFigure subFig = figureByScoped.get(sub.getScopedName(null));
                ClassFigure supFig = figureByScoped.get(sup.getScopedName(null));
                if (subFig != null && supFig != null) {
                    GeneralizationFigure g = new GeneralizationFigure();
                    drawing.add(g);
                    g.setStartConnector(subFig.connector());
                    g.setEndConnector(supFig.connector());
                    g.updateConnection();
                } else if (subFig != null && supFig == null) {
                    subFig.setForeignBaseLabel(formatTopicBaseLabel(sup)); // cross-topic base
                }
            }
        }

        if (showAssociations) {
            List<AssociationDef> allAssocs = collectAllAssociations(model);
            wireAssociationsForCanvas(drawing, allAssocs, figureByScoped);
        }

        JScrollPane sp = new JScrollPane(drawingView);
        installWheelZoom(drawingView, sp);
        installPanSupport(drawingView, editor, sp);
        return sp;
    }

    private static String card(RoleDef r) {
        Cardinality c = r.getCardinality();
        if (c == null) return "1";
        return c.toString().replace('{','[').replace('}',']');
    }

    private static String shortTargetLabel(Viewable v) {
        String scoped = v.getScopedName(null);
        if (scoped == null) return v.getName();
        String[] p = scoped.split("\\.");
        return (p.length >= 3) ? (p[p.length-2] + "::" + p[p.length-1]) : p[p.length-1];
    }

    private static void wireAssociationsForCanvas(
            Drawing drawing,
            List<AssociationDef> allAssocsInModel,
            Map<String, ClassFigure> figByScoped) {

        for (AssociationDef a : allAssocsInModel) {
            RoleDef r1 = null, r2 = null;
            for (Iterator<?> it = a.iterator(); it.hasNext();) {
                Object o = it.next();
                if (o instanceof RoleDef) {
                    if (r1 == null) r1 = (RoleDef) o;
                    else { r2 = (RoleDef) o; break; }
                }
            }
            if (r1 == null || r2 == null) continue;

            Viewable v1 = r1.getDestination();
            Viewable v2 = r2.getDestination();
            if (v1 == null || v2 == null) continue;

            String k1 = v1.getScopedName(null);
            String k2 = v2.getScopedName(null);
            ClassFigure f1 = figByScoped.get(k1);
            ClassFigure f2 = figByScoped.get(k2);

            if (f1 != null && f2 != null) {
                AssociationFigure edge = new AssociationFigure();
                edge.setStartMultiplicity(card(r1));
                edge.setEndMultiplicity(card(r2));
                edge.addTo(drawing);
                edge.setStartConnector(f1.connector());
                edge.setEndConnector(f2.connector());
                edge.updateConnection();
            } else if (f1 != null) {
                f1.addExternalRoleRow(r1.getName() + " " + card(r1)
                        + " : " + shortTargetLabel(v2) + "  «external»");
            } else if (f2 != null) {
                f2.addExternalRoleRow(r2.getName() + " " + card(r2)
                        + " : " + shortTargetLabel(v1) + "  «external»");
            }
        }
    }

    private void snapshotPositionsFromTabs() {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Component comp = tabs.getComponentAt(i);
            DefaultDrawingView dv = findDrawingView(comp);
            if (dv == null) continue;

            Drawing drawing = dv.getDrawing();
            List<Figure> figs = new ArrayList<>(drawing.getChildren());

            for (Figure f : figs) {
                if (f instanceof ClassFigure) {
                    ClassFigure cf = (ClassFigure) f;
                    Viewable owner = cf.getOwnerViewable(); // UPDATED
                    if (owner != null) {
                        String key = keyFor(owner);
                        if (key != null) {
                            Rectangle2D b = cf.getBounds();
                            rememberedPos.put(key, new Point2D.Double(b.getX(), b.getY()));
                        }
                    }
                }
            }
        }
    }

    private static DefaultDrawingView findDrawingView(Component comp) {
        if (comp instanceof JScrollPane) {
            Component v = ((JScrollPane) comp).getViewport().getView();
            if (v instanceof DefaultDrawingView) return (DefaultDrawingView) v;
        } else if (comp instanceof DefaultDrawingView) {
            return (DefaultDrawingView) comp;
        }
        return null;
    }

    private static List<AssociationDef> collectAllAssociations(Model model) {
        ArrayList<AssociationDef> out = new ArrayList<>();
        for (Iterator<?> it = model.iterator(); it.hasNext();) {
            Object o = it.next();
            if (o instanceof AssociationDef) out.add((AssociationDef) o);
            if (o instanceof Topic) {
                Topic tp = (Topic) o;
                for (Iterator<?> it2 = tp.iterator(); it2.hasNext();) {
                    Object p = it2.next();
                    if (p instanceof AssociationDef) out.add((AssociationDef) p);
                }
            }
        }
        return out;
    }

    private static TopicFigure addTopicFigure(Drawing drawing, Topic topic, int x, int y) {
        TopicFigure tf = new TopicFigure(topic);
        drawing.add(tf);
        tf.setBounds(new Point2D.Double(x, y), new Point2D.Double(x + 200, y + 140));
        tf.layout();
        return tf;
    }

    /** Adds a ClassFigure (now for any Viewable) to the drawing at (x,y). */
    private ClassFigure addViewableFigure(Drawing drawing, Viewable v, int x, int y) {
        ClassFigure cf = new ClassFigure(v);
        String key = keyFor(v);
        Color fill = (key != null) ? rememberedFills.get(key) : null;
        if (fill != null) cf.setBackgroundColor(fill);
        drawing.add(cf);

        cf.setBounds(new Point2D.Double(x, y), new Point2D.Double(x + 200, y + 120));
        cf.layout();
        return cf;
    }

    private static JComponent msgPanel(String msg) {
        JTextArea ta = new JTextArea(msg);
        ta.setEditable(false);
        ta.setOpaque(false);
        ta.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
        return new JScrollPane(ta);
    }

    private static void installWheelZoom(DefaultDrawingView view, JScrollPane scroller) {
        view.addMouseWheelListener(new MouseWheelListener() {
            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                if (!e.isControlDown()) return;
                int notches = e.getWheelRotation();
                double factor = Math.pow(WHEEL_STEP, -notches);
                zoomAt(view, scroller, factor, e.getPoint());
                e.consume();
            }
        });
    }

    private static void zoomAt(DefaultDrawingView view, JScrollPane scroller,
                               double factor, Point anchorInViewCoords) {
        double oldScale = view.getScaleFactor();
        double newScale = clamp(oldScale * factor, MIN_ZOOM, MAX_ZOOM);
        if (newScale == oldScale) return;

        Point2D.Double anchorInDrawing = view.viewToDrawing(anchorInViewCoords);

        view.setScaleFactor(newScale);
        view.revalidate();

        Point anchorAfterV = view.drawingToView(anchorInDrawing);

        JViewport vp = scroller.getViewport();
        Point viewPos = vp.getViewPosition();
        int dx = anchorAfterV.x - anchorInViewCoords.x;
        int dy = anchorAfterV.y - anchorInViewCoords.y;
        Point newPos = new Point(viewPos.x + dx, viewPos.y + dy);

        Dimension extent = vp.getExtentSize();
        Dimension viewSize = view.getPreferredSize();
        newPos.x = Math.max(0, Math.min(newPos.x, Math.max(0, viewSize.width  - extent.width)));
        newPos.y = Math.max(0, Math.min(newPos.y, Math.max(0, viewSize.height - extent.height)));

        vp.setViewPosition(newPos);
    }

    private static double clamp(double v, double lo, double hi) {
        return (v < lo) ? lo : (v > hi) ? hi : v;
    }

    private static void installPanSupport(DefaultDrawingView view, DrawingEditor editor, JScrollPane scroller) {
        final org.jhotdraw.draw.tool.SelectionTool selectionTool = new org.jhotdraw.draw.tool.SelectionTool();
        final PanTool panTool = new PanTool();
        editor.setTool(selectionTool);

        InputMap im = view.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = view.getActionMap();
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SPACE, 0, false), "pan.activate");
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SPACE, 0, true),  "pan.deactivate");
        am.put("pan.activate",   new AbstractAction(){ public void actionPerformed(java.awt.event.ActionEvent e){ editor.setTool(panTool); }});
        am.put("pan.deactivate", new AbstractAction(){ public void actionPerformed(java.awt.event.ActionEvent e){ editor.setTool(selectionTool); }});

        SwingUtilities.invokeLater(() -> {
            JRootPane root = SwingUtilities.getRootPane(view);
            if (root != null) {
                InputMap rim = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
                ActionMap ram = root.getActionMap();
                rim.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SPACE, 0, false), "pan.activate.root");
                rim.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SPACE, 0, true),  "pan.deactivate.root");
                ram.put("pan.activate.root",   am.get("pan.activate"));
                ram.put("pan.deactivate.root", am.get("pan.deactivate"));
            }
        });

        view.addMouseListener(new MouseAdapter() {
            private boolean tempPan = false;
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isMiddleMouseButton(e) || e.isAltDown()) {
                    tempPan = true;
                    editor.setTool(panTool);
                }
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (tempPan) {
                    tempPan = false;
                    editor.setTool(selectionTool);
                }
            }
        });
    }

    private void doZoomIn()  { withActiveZoomTarget((view, sp) -> zoomAt(view, sp, WHEEL_STEP, centerOfViewport(sp))); }
    private void doZoomOut() { withActiveZoomTarget((view, sp) -> zoomAt(view, sp, 1.0 / WHEEL_STEP, centerOfViewport(sp))); }
    private void doZoomReset(){ withActiveZoomTarget((view, sp) -> {
        double factor = 1.0 / view.getScaleFactor();
        zoomAt(view, sp, factor, centerOfViewport(sp));
    }); }

    private void withActiveZoomTarget(java.util.function.BiConsumer<DefaultDrawingView, JScrollPane> action) {
        JScrollPane sp = currentScroller();
        DefaultDrawingView view = currentView(sp);
        if (sp != null && view != null) action.accept(view, sp);
        else Toolkit.getDefaultToolkit().beep();
    }

    private JScrollPane currentScroller() {
        Component comp = tabs.getSelectedComponent();
        return (comp instanceof JScrollPane) ? (JScrollPane) comp : null;
    }
    private DefaultDrawingView currentView(JScrollPane sp) {
        if (sp == null) return null;
        Component v = sp.getViewport().getView();
        return (v instanceof DefaultDrawingView) ? (DefaultDrawingView) v : null;
    }

    private static Point centerOfViewport(JScrollPane scroller) {
        JViewport vp = scroller.getViewport();
        Dimension ext = vp.getExtentSize();
        return new Point(ext.width / 2, ext.height / 2);
    }

    /** Model.Topic.Class → Topic::Class; Model.Class → Class */
    private static String formatTopicBaseLabel(Viewable base) {
        String scoped = base.getScopedName(null);
        if (scoped == null) return base.getName();
        String[] p = scoped.split("\\.");
        if (p.length >= 3) return p[p.length - 2] + "::" + p[p.length - 1];
        return p[p.length - 1];
    }

    private static ClassFigure ownerOf(Figure f) {
        if (f == null) return null;
        if (f instanceof ClassFigure) return (ClassFigure) f;
        return f.get(ClassFigure.OWNER_KEY);
    }

    private static void installClassColorUI(DefaultDrawingView view) {
        final JPopupMenu popup = new JPopupMenu();
        final JMenuItem setFill   = new JMenuItem("Set fill…");
        final JMenuItem resetFill = new JMenuItem("Reset fill");
        popup.add(setFill);
        popup.add(resetFill);

        final Runnable repaint = view::repaint;

        setFill.addActionListener(e -> {
            Color initial = Color.white;
            for (Figure sf : view.getSelectedFigures()) {
                ClassFigure cf = ownerOf(sf);
                if (cf != null && cf.getBackgroundColor() != null) {
                    initial = cf.getBackgroundColor();
                    break;
                }
            }
            Color chosen = JColorChooser.showDialog(view.getComponent(), "Class fill color", initial);
            if (chosen == null) return;

            boolean applied = false;
            for (Figure sf : view.getSelectedFigures()) {
                ClassFigure cf = ownerOf(sf);
                if (cf != null) { cf.setBackgroundColor(chosen); applied = true; }
            }
            if (!applied) {
                Point p = MouseInfo.getPointerInfo().getLocation();
                SwingUtilities.convertPointFromScreen(p, view.getComponent());
                Figure f = view.findFigure(p);
                ClassFigure cf = ownerOf(f);
                if (cf != null) { cf.setBackgroundColor(chosen); }
            }
            repaint.run();
        });

        resetFill.addActionListener(e -> {
            for (Figure sf : view.getSelectedFigures()) {
                ClassFigure cf = ownerOf(sf);
                if (cf != null) cf.setBackgroundColor(null);
            }
            repaint.run();
        });

        view.getComponent().addMouseListener(new MouseAdapter() {
            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) popup.show(e.getComponent(), e.getX(), e.getY());
            }
            @Override public void mousePressed (MouseEvent e){ maybeShowPopup(e); }
            @Override public void mouseReleased(MouseEvent e){ maybeShowPopup(e); }
            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    Figure f = view.findFigure(e.getPoint());
                    ClassFigure cf = ownerOf(f);
                    if (cf != null) {
                        Color initial = (cf.getBackgroundColor() != null) ? cf.getBackgroundColor() : Color.white;
                        Color chosen = JColorChooser.showDialog(view.getComponent(), "Class fill color", initial);
                        if (chosen != null) {
                            boolean applied = false;
                            for (Figure sf : view.getSelectedFigures()) {
                                ClassFigure sel = ownerOf(sf);
                                if (sel != null) { sel.setBackgroundColor(chosen); applied = true; }
                            }
                            if (!applied) cf.setBackgroundColor(chosen);
                            view.repaint();
                        }
                    }
                }
            }
        });
    }

    /** Stable key for any Viewable. */
    private static String keyFor(Viewable v) {
        return (v != null) ? v.getScopedName(null) : null;
    }

    /** Back-compat (unused now, but safe to keep). */
    private static String keyFor(Table t) {
        return (t != null) ? t.getScopedName(null) : null;
    }
}
