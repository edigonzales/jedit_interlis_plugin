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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import ch.interlis.ili2c.metamodel.*;
import ch.so.agi.jedit.compile.TdCache;

// JHotDraw 7.6
import org.jhotdraw.draw.DefaultDrawing;
import org.jhotdraw.draw.DefaultDrawingEditor;
import org.jhotdraw.draw.DefaultDrawingView;
import org.jhotdraw.draw.Drawing;
import org.jhotdraw.draw.DrawingEditor;
import org.jhotdraw.draw.Figure;
import org.jhotdraw.draw.RectangleFigure;
import org.jhotdraw.draw.TextFigure;
import org.jhotdraw.draw.connector.ChopRectangleConnector;
import org.jhotdraw.draw.tool.SelectionTool;

public final class UmlDockable extends JPanel {

    private static final String PREF_REMEMBER_POS = "interlis.uml.rememberPositions";
    
    private static final String TAB_KEY = "uml.tab.key";
    
    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 4.0;
    private static final double WHEEL_STEP = 1.1; // 10% per notch (fine-tune to taste)

    private final View view;
    private final String position;

    private final JTabbedPane tabs = new JTabbedPane();
    private final JButton refreshBtn = new JButton("Refresh");

    private final JButton zoomOutBtn  = new JButton("–");
    private final JButton zoomResetBtn= new JButton("100%");
    private final JButton zoomInBtn   = new JButton("+");
    
    private final JButton menuBtn = new JButton("⋮"); // or "\u2699" (gear)
    private final JPopupMenu tbMenu = buildToolbarMenu();
    
    private static boolean showAssociations = true;  // default on
    
    private final java.util.Map<String, java.awt.geom.Point2D.Double> rememberedPos = new java.util.HashMap<>();
    private boolean rememberPositions = jEdit.getBooleanProperty(PREF_REMEMBER_POS, true);
    private final Map<String, java.awt.Color> rememberedFills = new java.util.HashMap<>();
    
    public UmlDockable(View view, String position) {
        super(new BorderLayout(8, 8));
        this.view = view;
        this.position = position;

        showAssociations = jEdit.getBooleanProperty("interlis.uml.showAssociations", true);
        
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Toolbar
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.setRollover(true);

//        tb.add(new JLabel("INTERLIS – UML diagram"));
//        tb.add(Box.createHorizontalStrut(12));

        compact(refreshBtn);
        tb.add(refreshBtn);

        // replace JSeparator with a very thin ToolBar separator
        tb.add(Box.createHorizontalStrut(8));
        tb.add(thinSep(6));                 // ← only ~6px wide
        tb.add(Box.createHorizontalStrut(4)); // ← small, symmetric gap *after* the line

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

        // Show the popup right under the button
        menuBtn.addActionListener(e ->
            tbMenu.show(menuBtn, 0, menuBtn.getHeight())
        );
        
        tb.add(Box.createHorizontalGlue()); // keep at the very end
        add(tb, BorderLayout.NORTH);

        // actions
        zoomOutBtn.addActionListener(e -> doZoomOut());
        zoomResetBtn.addActionListener(e -> doZoomReset());
        zoomInBtn.addActionListener(e -> doZoomIn());
        
        add(tb, BorderLayout.NORTH);

        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        add(tabs, BorderLayout.CENTER);

        refreshBtn.addActionListener(e -> rebuildFromCache());

        // initial build
        rebuildFromCache();
    }

    private static void compact(JButton b) {
        b.setFocusable(false);
        b.setMargin(new Insets(2, 8, 2, 8));
    }
    
    private static JToolBar.Separator thinSep(int px) {
        JToolBar.Separator s = new JToolBar.Separator(new Dimension(px, 0));
        s.setMaximumSize(new Dimension(px, Integer.MAX_VALUE)); // don't let L&F widen it
        return s;
    }
    
    private JPopupMenu buildToolbarMenu() {
        JPopupMenu m = new JPopupMenu();

//        JCheckBoxMenuItem showInheritance = new JCheckBoxMenuItem("Show inheritance", true);
//        showInheritance.addActionListener(e -> {
//            // toggle flag you use when drawing edges, then rebuild
//            // this.showInheritance = showInheritance.isSelected();
//            rebuildFromCache();
//        });
//        m.add(showInheritance);

        // Remember positions toggle (persisted)
        JCheckBoxMenuItem miRemember =
                new JCheckBoxMenuItem("Remember positions", rememberPositions);
        miRemember.addActionListener(e -> {
            rememberPositions = miRemember.isSelected();
            jEdit.setBooleanProperty(PREF_REMEMBER_POS, rememberPositions);
            if (!rememberPositions) {
                rememberedPos.clear();   // drop cache
            } else {
                snapshotPositionsFromTabs(); // capture current before rebuild
            }
            rebuildFromCache();
        });
        m.add(miRemember);
        
        JCheckBoxMenuItem miShowAssoc = new JCheckBoxMenuItem("Show associations", showAssociations);
        miShowAssoc.addActionListener(e -> {
            showAssociations = miShowAssoc.isSelected();
            jEdit.setBooleanProperty("interlis.uml.showAssociations", showAssociations);
            rebuildFromCache(); // rebuild all tabs with new setting
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
        
//        m.addSeparator();
//
//        JMenu layout = new JMenu("Layout");
//        JMenuItem autoGrid = new JMenuItem("Auto place (grid)");
//        autoGrid.addActionListener(e -> {
//            // call your layout routine on the active tab’s drawing
//            // autoPlaceCurrentCanvas();
//        });
//        layout.add(autoGrid);
//
//        JMenuItem tidy = new JMenuItem("Tidy selection");
//        tidy.addActionListener(e -> {
//            // tidySelectedFigures();
//        });
//        layout.add(tidy);
//        m.add(layout);
//
//        m.addSeparator();
//
//        JMenuItem themeColors = new JMenuItem("Class fill color…");
//        themeColors.addActionListener(e -> {
//            // reuse your color chooser: maybe apply to selection
//            // openFillColorChooserForSelection();
//        });
//        m.add(themeColors);
//
//        JMenu reset = new JMenu("Reset");
//        JMenuItem resetFills = new JMenuItem("Reset class fills");
//        resetFills.addActionListener(e -> {
//            // reset all fills on current drawing
//            // resetAllClassFills();
//        });
//        reset.add(resetFills);
//        m.add(reset);

        return m;
    }
    
    /** Rebuild tabs from the latest cached TD (non-blocking; shows hint if none). */
    private void rebuildFromCache() {
        if (rememberPositions) {
            snapshotPositionsFromTabs();
        }
        
        maybeLoadUmlFromFile();

        // Remember current selection
        Object prevKey = null;
        int prevIndex  = tabs.getSelectedIndex();
        if (prevIndex >= 0 && prevIndex < tabs.getTabCount()) {
            java.awt.Component c = tabs.getComponentAt(prevIndex);
            if (c instanceof JComponent) {
                prevKey = ((JComponent) c).getClientProperty(TAB_KEY);
            }
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

        java.util.Set<String> liveKeys = new java.util.HashSet<>();
        
        tabs.removeAll();
        Model[] models = td.getModelsFromLastFile();
        if (models == null || models.length == 0) {
            tabs.addTab("No data", msgPanel("No models in last file."));
            return;
        }

        int indexToSelect = -1;
        
        final java.util.Map<String, java.awt.geom.Point2D.Double> posCache =
                rememberPositions ? rememberedPos : null;
                
        for (Model model : models) {
            List<Topic> topics = collectTopics(model);
            List<Table> modelClasses = collectModelLevelClasses(model);

            for (Table t : modelClasses) {
                String k = keyFor(t);
                if (k != null) liveKeys.add(k);
            }
            
            // Single tab: topics + model-level classes together
            if (topics.isEmpty() && modelClasses.isEmpty()) {
                tabs.addTab(model.getName(), msgPanel("No topics or model-level classes."));
            } else {
//                tabs.addTab(model.getName(), canvasForOverview(topics, modelClasses));
                JComponent overview = canvasForOverview(topics, modelClasses, posCache);
                String keyOverview  = "model:" + model.getName() + ":overview";
                ((JComponent) overview).putClientProperty(TAB_KEY, keyOverview);
                tabs.addTab(model.getName(), overview);
                if (prevKey != null && prevKey.equals(keyOverview)) {
                    indexToSelect = tabs.indexOfComponent(overview);
                }

            }

            // One tab per TOPIC for its classes (kept as before)
            for (Topic topic : topics) {
                List<Table> topicClasses = collectTopicClasses(topic);
                
                for (Table t : topicClasses) {
                    String k = keyFor(t);
                    if (k != null) liveKeys.add(k);
                }
                
                String tabTitle = /*model.getName() + "::" +*/ topic.getName();
                
                if (topicClasses.isEmpty()) {
                    tabs.addTab(tabTitle, msgPanel("No classes in this topic."));
                } else {
                    JComponent topicCanvas = canvasFor(topicClasses, model, posCache);
                    String keyTopic = "topic:" + model.getName() + "::" + topic.getName();
                    ((JComponent) topicCanvas).putClientProperty(TAB_KEY, keyTopic);
                    tabs.addTab(topic.getName(), topicCanvas);
                    if (prevKey != null && prevKey.equals(keyTopic)) {
                        indexToSelect = tabs.indexOfComponent(topicCanvas);
                    }
                }
            }
        }
        
        // Restore selection
        if (indexToSelect >= 0) {
            tabs.setSelectedIndex(indexToSelect);
        } else if (prevIndex >= 0 && prevIndex < tabs.getTabCount()) {
            // fallback: same index if key wasn’t found
            tabs.setSelectedIndex(prevIndex);
        } else if (tabs.getTabCount() > 0) {
            tabs.setSelectedIndex(0);
        }
        
        if (rememberPositions) {
            pruneRememberedTo(liveKeys);
        }
        
        tabs.revalidate();
        tabs.repaint();
    }

    private void pruneRememberedTo(java.util.Collection<String> liveKeys) {
        rememberedPos.keySet().retainAll(liveKeys);
    }
    
    private java.io.File umlFileForBuffer() {
        Buffer b = view.getBuffer();
        if (b == null) return null;
        String dir = b.getDirectory();
        String name = b.getName(); // e.g. MyModel.ili
        String base = name.replaceFirst("\\.[^.]*$", ""); // strip extension
        return new java.io.File(dir, base + ".uml");
    }

    private static String colorToHex(java.awt.Color c) {
        if (c == null) return null;
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static java.awt.Color hexToColor(String hex) {
        if (hex == null || hex.isEmpty()) return null;
        try {
            return java.awt.Color.decode(hex);
        } catch (Exception e) {
            return null;
        }
    }

    private void saveUmlToFile() {
        try {
            // Make sure we capture the latest on-screen state
            snapshotStateFromTabs();

            java.io.File out = umlFileForBuffer();
            if (out == null) { Toolkit.getDefaultToolkit().beep(); return; }

            StringBuilder sb = new StringBuilder(16_384);
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<uml version=\"1\">\n");

            // Options
            sb.append("  <options showAssociations=\"").append(showAssociations).append("\"/>\n");

            // Class positions
            sb.append("  <classes>\n");
            for (Map.Entry<String, java.awt.geom.Point2D.Double> e : rememberedPos.entrySet()) {
                String scoped = e.getKey();
                java.awt.geom.Point2D.Double p = e.getValue();
                java.awt.Color fill = rememberedFills.get(scoped);
                sb.append("    <class scoped=\"").append(escapeXml(scoped)).append("\" ")
                  .append("x=\"").append(p.x).append("\" ")
                  .append("y=\"").append(p.y).append("\"");
                if (fill != null) sb.append(" fill=\"").append(colorToHex(fill)).append("\"");
                sb.append("/>\n");
            }
            sb.append("  </classes>\n");

            sb.append("</uml>\n");

            // Write atomically
            java.io.File tmp = java.io.File.createTempFile("uml_", ".xml", out.getParentFile());
            try (java.io.OutputStream os = new java.io.BufferedOutputStream(new java.io.FileOutputStream(tmp))) {
                os.write(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(out)) {
                // fallback
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
        if (!rememberedPos.isEmpty() || !rememberedFills.isEmpty()) return; // already have state

        java.io.File f = umlFileForBuffer();
        if (f == null || !f.isFile()) return;

        System.err.println("******** maybeLoadUmlFromFile");
        
        
        try {
            org.w3c.dom.Document doc = javax.xml.parsers.DocumentBuilderFactory
                    .newInstance().newDocumentBuilder().parse(f);
            doc.getDocumentElement().normalize();

            // options
            org.w3c.dom.NodeList opts = doc.getElementsByTagName("options");
            if (opts.getLength() > 0) {
                org.w3c.dom.Element e = (org.w3c.dom.Element) opts.item(0);
                String sa = e.getAttribute("showAssociations");
                if (!sa.isEmpty()) {
                    showAssociations = Boolean.parseBoolean(sa);
                    jEdit.setBooleanProperty("interlis.uml.showAssociations", showAssociations);
                }
            }

            // classes
            org.w3c.dom.NodeList cls = doc.getElementsByTagName("class");
            for (int i = 0; i < cls.getLength(); i++) {
                org.w3c.dom.Element e = (org.w3c.dom.Element) cls.item(i);
                String scoped = e.getAttribute("scoped");
                
                System.err.println("******** scoped: " + scoped);

                if (scoped == null || scoped.isEmpty()) continue;
                double x = Double.parseDouble(e.getAttribute("x"));
                double y = Double.parseDouble(e.getAttribute("y"));
                rememberedPos.put(scoped, new java.awt.geom.Point2D.Double(x, y));
                String fill = e.getAttribute("fill");
                if (fill != null && !fill.isEmpty()) {
                    java.awt.Color c = hexToColor(fill);
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
            java.awt.Component comp = tabs.getComponentAt(i);
            DefaultDrawingView dv = findDrawingView(comp);
            if (dv == null) continue;

            Drawing drawing = dv.getDrawing();
            for (Figure f : new java.util.ArrayList<>(drawing.getChildren())) {
                if (f instanceof ClassFigure) {
                    ClassFigure cf = (ClassFigure) f;
                    ch.interlis.ili2c.metamodel.Table owner = cf.getOwnerTable();
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
        if (view == null) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        String base = fileBaseForTab(tabs.getSelectedComponent());
        File out = new File(dir, base + ".png");
        try {
            exportViewToPNG(view, out, 2.0); // scale=1.0 (independent of UI zoom)
            //exportViewToPNG(view, out); 
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
                //exportViewToPNG(dv, out);
                exportViewToPNG(dv, out, 2.0);
                ok++;
            } catch (Exception ex) {
                failed++;
            }
        }
        JOptionPane.showMessageDialog(this,
            "PNG export finished.\nSaved: " + ok + (skipped>0?("\nSkipped (no diagram): "+skipped):"")
            + (failed>0?("\nFailed: "+failed):""),
            "Export PNG", JOptionPane.INFORMATION_MESSAGE);
    }

    /* ---- Low-level: render drawing to PNG ---- */

 // ==== EXPORT: draw figures safely, without DefaultDrawing.draw ====
 // Export at arbitrary DPI scale (1.0 = 100%, 2.0 = 200% ...)
    private static void exportViewToPNG(DefaultDrawingView view, File outFile, double scale) throws Exception {
        Drawing drawing = view.getDrawing();

        // 1) Make sure connections are up-to-date
        for (Figure f : new ArrayList<>(drawing.getChildren())) {
            if (f instanceof org.jhotdraw.draw.ConnectionFigure) {
                ((org.jhotdraw.draw.ConnectionFigure) f).updateConnection();
            }
        }

        // 2) Compute union in *drawing* coordinates (unscaled)
        Rectangle2D union = computeUnionBounds(drawing);
        if (union == null || union.isEmpty()) {
            throw new IllegalStateException("Nothing to export (no visible bounds).");
        }

        // 3) Allocate image in *device* pixels
        int w = Math.max(1, (int) Math.ceil(union.getWidth()  * scale));
        int h = Math.max(1, (int) Math.ceil(union.getHeight() * scale));
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Background
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, w, h);

            // 4) IMPORTANT: apply transform before drawing
            //    scale first, then translate by the *unscaled* union
            g2.scale(scale, scale);
            g2.translate(-union.getX(), -union.getY());

            // 5) Draw children (no clip needed; avoids null-area issues)
            for (Figure f : new ArrayList<>(drawing.getChildren())) {
                f.draw(g2);
            }
        } finally {
            g2.dispose();
        }
        ImageIO.write(img, "png", outFile);
    }

    /** Returns a safe area for a figure or null if none can be determined. */
    private static java.awt.geom.Rectangle2D safeDrawingArea(Figure f) {
        try {
            java.awt.geom.Rectangle2D a = f.getDrawingArea();
            if (a != null && !a.isEmpty()) return (java.awt.geom.Rectangle2D) a.clone();
        } catch (Throwable ignore) {}

        try {
            // Fallbacks
            java.awt.geom.Rectangle2D b = f.getBounds();
            if (b != null && !b.isEmpty()) return (java.awt.geom.Rectangle2D) b.clone();

            if (f instanceof org.jhotdraw.draw.BezierFigure) {
                org.jhotdraw.geom.BezierPath p = ((org.jhotdraw.draw.BezierFigure) f).getBezierPath();
                if (p != null) {
                    java.awt.geom.Rectangle2D pb = p.getBounds2D();
                    if (pb != null && !pb.isEmpty()) return (java.awt.geom.Rectangle2D) pb.clone();
                }
            }
        } catch (Throwable ignore) {}

        return null;
    }

    /** Union of all figure areas; skips figures that report null. */
    private static java.awt.geom.Rectangle2D computeUnionBounds(Drawing d) {
        java.awt.geom.Rectangle2D union = null;
        for (Figure f : new java.util.ArrayList<>(d.getChildren())) {
            java.awt.geom.Rectangle2D r = safeDrawingArea(f);
            if (r == null) continue;
            union = (union == null) ? (java.awt.geom.Rectangle2D) r.clone() : union.createUnion(r);
        }
        return union;
    }

    
    /* ---- Naming & directory helpers ---- */

    private File currentBufferDir() {
        Buffer buf = view.getBuffer();
        String dir = (buf != null) ? buf.getDirectory() : null; // jEdit Buffer
        if (dir == null) return null;
        return new File(dir);
    }

    private String fileBaseForTab(Component tabComp) {
        if (!(tabComp instanceof JComponent)) {
            // fallback to tab title
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
        String key = (String) keyObj; // e.g. "model:ModelName:overview" or "topic:Model::Topic"
        try {
            if (key.startsWith("topic:")) {
                String[] parts = key.substring("topic:".length()).split("::"); // ["Model","Topic"]
                if (parts.length == 2) return sanitizeBase(parts[0] + "_" + parts[1]);
            } else if (key.startsWith("model:")) {
                String rest = key.substring("model:".length()); // "ModelName:overview" or similar
                String model = rest;
                int c = rest.indexOf(':');
                if (c >= 0) model = rest.substring(0, c);
                return sanitizeBase(model + "_overview");
            }
        } catch (Exception ignored) {}
        // fallback
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

    /** All topics declared directly in the model. */
    private static List<Topic> collectTopics(Model model) {
        ArrayList<Topic> out = new ArrayList<>();
        for (Iterator<?> it = model.iterator(); it.hasNext();) {
            Object o = it.next();
            if (o instanceof Topic) out.add((Topic) o);
        }
        return out;
    }

    /** Tables (classes, structures) declared directly in the model (not inside any topic). */
    private static List<Table> collectModelLevelClasses(Model model) {
        ArrayList<Table> out = new ArrayList<>();
        for (Iterator<?> it = model.iterator(); it.hasNext();) {
            Object o = it.next();
            if (o instanceof Topic) continue; // skip topics here
//            if (o instanceof Table) {
//                Table t = (Table) o;
//                out.add(t); // classes and structures
//            }
        }
        return out;
    }

    /** Tables (classes) declared inside a topic. */
    private static List<Table> collectTopicClasses(Topic topic) {
        ArrayList<Table> out = new ArrayList<>();
        for (Iterator<?> it = topic.iterator(); it.hasNext();) {
            Object o = it.next();
//            if (o instanceof Table) {
//                Table t = (Table) o;
//                out.add(t); // classes and structures
//            }
            if (o instanceof Viewable) {
                Viewable v = (Viewable) o;
                out.add(v); // classes and structures
            }

        }
        return out;
    }

    /* ============================== canvases ================================= */

    /** Overview canvas: topics + model-level classes in one grid. */
    private  JComponent canvasForOverview(List<Topic> topics, List<Table> classes, Map<String, Point2D.Double> posCache) {
        DefaultDrawingView drawingView = new DefaultDrawingView();
        drawingView.setBackground(Color.white);

        DrawingEditor editor = new DefaultDrawingEditor();
        editor.add(drawingView);
        editor.setActiveView(drawingView);

        Drawing drawing = new DefaultDrawing();
        drawingView.setDrawing(drawing);
        
        installClassColorUI(drawingView);

        java.util.Map<String, ClassFigure> figureByScoped = new java.util.HashMap<>();
        
        int total = topics.size() + classes.size();
        if (total == 0) return new JScrollPane(drawingView);

        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(total)));
        int cellW = 320; // a bit larger to accommodate TopicFigure body
        int cellH = 200;
        int gap   = 30;

        int i = 0;

        Map<Topic, TopicFigure> byTopic = new HashMap<>();
        
        // Place topics first
        for (Topic t : topics) {
            int row = i / cols;
            int col = i % cols;
            int x = gap + col * (cellW + gap);
            int y = gap + row * (cellH + gap);
            TopicFigure tf = addTopicFigure(drawing, t, x, y);
            byTopic.put(t, tf);
            i++;
        }
        
        addTopicDependencyEdges(drawing, topics, byTopic);

        // Then model-level classes
        for (Table c : classes) {
            int row = i / cols;
            int col = i % cols;
            int x = gap + col * (cellW + gap);
            int y = gap + row * (cellH + gap);
            ClassFigure cf = addClassFigure(drawing, c, x, y);
            
            // restore origin if cached
            if (posCache != null) {
                String key = keyFor(c);
                java.awt.geom.Point2D.Double p = (key != null) ? posCache.get(key) : null;
                if (p != null) {
                    java.awt.geom.Rectangle2D nb = cf.getBounds();
                    cf.setBounds(new Point2D.Double(p.x, p.y),
                                 new Point2D.Double(p.x + nb.getWidth(), p.y + nb.getHeight()));
                }
            }

            
            String key = c.getScopedName(null);
            figureByScoped.put(key, cf);
            
            i++;
        }

        // add inheritance edges (direct base only; you can loop ancestors if you want)
        for (Table sub : classes) {
            Element ext = sub.getExtending();
            if (ext instanceof Table) {
                Table sup = (Table) ext; // superclass (class OR structure)

                ClassFigure subFig = figureByScoped.get(sub.getScopedName(null));
                ClassFigure supFig = figureByScoped.get(sup.getScopedName(null));

                if (subFig != null && supFig != null) {
                    GeneralizationFigure g = new GeneralizationFigure();
                    drawing.add(g); // add after nodes so it draws on top
                    g.setStartConnector(subFig.connector()); // subclass
                    g.setEndConnector(supFig.connector());   // superclass (tip points here)
                    g.updateConnection(); // be explicit
                } else {
                    // superclass not on this canvas (e.g., different topic/tab) -> skip or add a stub label
                }
            }
        }
        
        JScrollPane sp = new JScrollPane(drawingView);
        installWheelZoom(drawingView, sp);     // your existing zoom helper
        installPanSupport(drawingView, editor, sp); // <-- add this
        return sp;
    }

    private static void addTopicDependencyEdges(
            Drawing drawing,
            List<Topic> topics,
            Map<Topic, TopicFigure> byTopic) {

        for (Topic t : topics) {
            for (Topic dep : topicDependsOn(t)) { // t DEPENDS ON dep
                TopicFigure src = byTopic.get(t);
                TopicFigure dst = byTopic.get(dep);
                if (src == null || dst == null) {
                    // dependency points to a topic not shown on this canvas → ignore or add a stub label if you want
                    continue;
                }
                DependsConnectionFigure cf = new DependsConnectionFigure();
                drawing.add(cf);
                cf.setStartConnector(src.connector()); // start = topic that HAS the dependency
                cf.setEndConnector(dst.connector());   // end = depended-on topic (arrow points here)
                // JHotDraw will keep it attached as you move topics
            }
        }
    }
    
    private static List<Topic> topicDependsOn(Topic t) {
        List<Topic> out = new ArrayList<>();
        if (t == null) return out;

        Iterator<Topic> it = t.getDependentOn(); // ili2c: returns an Iterator
        if (it == null) return out;

        while (it.hasNext()) {
            Topic dep = it.next();
            if (dep != t) {
                out.add(dep); // skip self, just in case
            }
        }
        return out;
    }
    
    
    /** Creates a scrollable JHotDraw canvas and lays out class boxes in a grid. */
    private  JComponent canvasFor(List<Table> classes, Model model, Map<String, Point2D.Double> posCache) {
        DefaultDrawingView drawingView = new DefaultDrawingView();
        drawingView.setBackground(Color.white);

        DrawingEditor editor = new DefaultDrawingEditor();
        editor.add(drawingView);
        editor.setActiveView(drawingView);

        Drawing drawing = new DefaultDrawing();
        drawingView.setDrawing(drawing);
        
        installClassColorUI(drawingView);
        
        java.util.Map<String, ClassFigure> figureByScoped = new java.util.HashMap<>();

        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(classes.size())));
        int cellW = 280;
        int cellH = 180;
        int gap   = 30;

        int i = 0;
        for (Table c : classes) {
            int row = i / cols;
            int col = i % cols;
            int x = gap + col * (cellW + gap);
            int y = gap + row * (cellH + gap);

            ClassFigure cf = addClassFigure(drawing, c, x, y);
            
            // restore origin if cached
            if (posCache != null) {
                String key = keyFor(c);
                java.awt.geom.Point2D.Double p = (key != null) ? posCache.get(key) : null;
                if (p != null) {
                    java.awt.geom.Rectangle2D nb = cf.getBounds();
                    cf.setBounds(new Point2D.Double(p.x, p.y),
                                 new Point2D.Double(p.x + nb.getWidth(), p.y + nb.getHeight()));
                }
            }
            
            String key = c.getScopedName(null);
            figureByScoped.put(key, cf);

            i++;
        }

        // add inheritance edges (direct base only; you can loop ancestors if you want)
        for (Table sub : classes) {
            Element ext = sub.getExtending();
            if (ext instanceof Table) {
                Table sup = (Table) ext; // superclass (class OR structure)

                ClassFigure subFig = figureByScoped.get(sub.getScopedName(null));
                ClassFigure supFig = figureByScoped.get(sup.getScopedName(null));

                if (subFig != null && supFig != null) {
                    GeneralizationFigure g = new GeneralizationFigure();
                    drawing.add(g); // add after nodes so it draws on top
                    g.setStartConnector(subFig.connector()); // subclass
                    g.setEndConnector(supFig.connector());   // superclass (tip points here)
                    g.updateConnection(); // be explicit
                } else {
                    // superclass not on this canvas (e.g., different topic/tab) -> skip or add a stub label
                    subFig.setForeignBaseLabel(formatTopicBaseLabel(sup));
                }
            }
        }
        
//        java.util.List<Object> scope = new java.util.ArrayList<>();
//        for (java.util.Iterator<?> it = topic.iterator(); it.hasNext();) {
//            scope.add(it.next());
//        }
//        wireAssociations(drawing, scope, figureByScoped);
        
        if (showAssociations) {
            List<AssociationDef> allAssocs = collectAllAssociations(model);
            wireAssociationsForCanvas(drawing, allAssocs, figureByScoped);
        }

        JScrollPane sp = new JScrollPane(drawingView);
        installWheelZoom(drawingView, sp);     // your existing zoom helper
        installPanSupport(drawingView, editor, sp); // <-- add this
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
            java.util.Map<String, ClassFigure> figByScoped) {

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

            Viewable v1 = r1.getDestination(); // <- not getTarget()
            Viewable v2 = r2.getDestination();
            if (!(v1 instanceof Table) || !(v2 instanceof Table)) continue;

            String k1 = ((Table) v1).getScopedName(null);
            String k2 = ((Table) v2).getScopedName(null);
            ClassFigure f1 = figByScoped.get(k1);
            ClassFigure f2 = figByScoped.get(k2);

            if (f1 != null && f2 != null) {
                // both ends on this canvas → draw connection + multiplicities
                AssociationFigure edge = new AssociationFigure();
                edge.setStartMultiplicity(card(r1));
                edge.setEndMultiplicity(card(r2));
                edge.addTo(drawing);                 // OK before/after setting text
                edge.setStartConnector(f1.connector());
                edge.setEndConnector(f2.connector());
                edge.updateConnection();
            } else if (f1 != null) {
                // only left end visible here → show external row on left
                f1.addExternalRoleRow(r1.getName() + " " + card(r1)
                        + " : " + shortTargetLabel(v2) + "  «external»");
            } else if (f2 != null) {
                // only right end visible here → show external row on right
                f2.addExternalRoleRow(r2.getName() + " " + card(r2)
                        + " : " + shortTargetLabel(v1) + "  «external»");
            }
        }
    }
    
//    private static void wireAssociations(
//            Drawing drawing,
//            Iterable<?> scopeElements,                 // e.g., model or topic via their iterator()
//            java.util.Map<String, ClassFigure> figByScoped) {
//
//        java.util.ArrayList<AssociationDef> assocs = new java.util.ArrayList<>();
//        for (Object o : scopeElements) if (o instanceof AssociationDef) assocs.add((AssociationDef) o);
//
//        for (AssociationDef a : assocs) {
//            RoleDef r1 = null, r2 = null;
//            for (java.util.Iterator<?> it = a.iterator(); it.hasNext();) {
//                Object o = it.next();
//                if (o instanceof RoleDef) {
//                    if (r1 == null) r1 = (RoleDef) o;
//                    else { r2 = (RoleDef) o; break; }
//                }
//            }
//            if (r1 == null || r2 == null) continue;
//
//            Viewable t1 = r1.getDestination();
//            Viewable t2 = r2.getDestination();
//            if (!(t1 instanceof Table) || !(t2 instanceof Table)) continue;
//
//            String k1 = ((Table) t1).getScopedName(null);
//            String k2 = ((Table) t2).getScopedName(null);
//            ClassFigure f1 = figByScoped.get(k1);
//            ClassFigure f2 = figByScoped.get(k2);
//
//            if (f1 != null && f2 != null) {
//                AssociationFigure edge = new AssociationFigure();
//                edge.setStartMultiplicity(card(r1));
//                edge.setEndMultiplicity(card(r2));
//                edge.addTo(drawing);
//                edge.setStartConnector(f1.connector());
//                edge.setEndConnector(f2.connector());
//                edge.updateConnection();
//            } else if (f1 != null && f2 == null) {
//                f1.addExternalRoleRow(r1.getName() + " " + card(r1) + " : " + shortTargetLabel(t2) + "  «external»");
//            } else if (f2 != null && f1 == null) {
//                f2.addExternalRoleRow(r2.getName() + " " + card(r2) + " : " + shortTargetLabel(t1) + "  «external»");
//            }
//        }
//    }
    
    private void snapshotPositionsFromTabs() {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            java.awt.Component comp = tabs.getComponentAt(i);
            org.jhotdraw.draw.DefaultDrawingView dv = findDrawingView(comp);
            if (dv == null) continue;

            org.jhotdraw.draw.Drawing drawing = dv.getDrawing();

            // Copy to avoid concurrent modification if anything changes while iterating
            java.util.List<org.jhotdraw.draw.Figure> figs =
                    new java.util.ArrayList<>(drawing.getChildren());

            for (org.jhotdraw.draw.Figure f : figs) {
                if (f instanceof ClassFigure) {
                    ClassFigure cf = (ClassFigure) f;
                    ch.interlis.ili2c.metamodel.Table owner = cf.getOwnerTable();
                    if (owner != null) {
                        String key = keyFor(owner);
                        if (key != null) {
                            java.awt.geom.Rectangle2D b = cf.getBounds();
                            rememberedPos.put(key,
                                    new java.awt.geom.Point2D.Double(b.getX(), b.getY()));
                        }
                    }
                }
            }
        }
    }

    private static org.jhotdraw.draw.DefaultDrawingView findDrawingView(java.awt.Component comp) {
        if (comp instanceof javax.swing.JScrollPane) {
            java.awt.Component v = ((javax.swing.JScrollPane) comp).getViewport().getView();
            if (v instanceof org.jhotdraw.draw.DefaultDrawingView) return (org.jhotdraw.draw.DefaultDrawingView) v;
        } else if (comp instanceof org.jhotdraw.draw.DefaultDrawingView) {
            return (org.jhotdraw.draw.DefaultDrawingView) comp;
        }
        return null;
    }

    
    private static List<AssociationDef> collectAllAssociations(Model model) {
        ArrayList<AssociationDef> out = new ArrayList<>();
        // model-level
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
    
    /** Adds a TopicFigure (UML package) to the drawing at (x,y). */
    private static TopicFigure addTopicFigure(Drawing drawing, Topic topic, int x, int y) {
        TopicFigure tf = new TopicFigure(topic);
        drawing.add(tf);

        // Initial anchor; TopicFigure computes its own size in layout()
        tf.setBounds(new Point2D.Double(x, y), new Point2D.Double(x + 200, y + 140));
        tf.layout(); // ensure natural size is applied
        
        return tf;
    }

    /** Adds a ClassFigure to the drawing at (x,y). */
    private  ClassFigure addClassFigure(Drawing drawing, Table clazz, int x, int y) {
        ClassFigure cf = new ClassFigure(clazz);
        String key = keyFor(clazz);
        java.awt.Color fill = (key != null) ? rememberedFills.get(key) : null;
        if (fill != null) cf.setBackgroundColor(fill);
        drawing.add(cf);

        // Initial anchor; ClassFigure computes size in layout()
        cf.setBounds(new Point2D.Double(x, y), new Point2D.Double(x + 200, y + 120));
        cf.layout(); // ensure natural size is applied
        
        return cf;
    }

    private static JComponent msgPanel(String msg) {
        JTextArea ta = new JTextArea(msg);
        ta.setEditable(false);
        ta.setOpaque(false);
        ta.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
        return new JScrollPane(ta);
    }
    
    /** Install Ctrl+Wheel zoom on a drawing view within a scroll pane. */
    private static void installWheelZoom(DefaultDrawingView view, JScrollPane scroller) {
        view.addMouseWheelListener(new MouseWheelListener() {
            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                if (!e.isControlDown()) return; // keep plain wheel for scrolling
                int notches = e.getWheelRotation();          // negative = up = zoom in
                double factor = Math.pow(WHEEL_STEP, -notches);
                zoomAt(view, scroller, factor, e.getPoint()); // anchor is mouse position
                e.consume();
            }
        });
    }

    /** Zoom by 'factor' around view-space anchor (e.g., mouse location). */
    private static void zoomAt(DefaultDrawingView view, JScrollPane scroller,
                               double factor, Point anchorInViewCoords) {
        double oldScale = view.getScaleFactor();
        double newScale = clamp(oldScale * factor, MIN_ZOOM, MAX_ZOOM);
        if (newScale == oldScale) return;

        // BEFORE zoom: convert cursor (view coords) to drawing coords
        java.awt.geom.Point2D.Double anchorInDrawing = view.viewToDrawing(anchorInViewCoords);

        // Apply zoom
        view.setScaleFactor(newScale);
        view.revalidate(); // update preferred size for scrollbars

        // AFTER zoom: convert same drawing point back to view coords (returns Point)
        Point anchorAfterV = view.drawingToView(anchorInDrawing);

        // Scroll so that the anchor stays under the cursor
        JViewport vp = scroller.getViewport();
        Point viewPos = vp.getViewPosition();
        int dx = anchorAfterV.x - anchorInViewCoords.x;
        int dy = anchorAfterV.y - anchorInViewCoords.y;
        Point newPos = new Point(viewPos.x + dx, viewPos.y + dy);

        // Clamp
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
        editor.setTool(selectionTool); // default

        // ---- A) Spacebar key binding at the VIEW level (works on most platforms)
        InputMap im = view.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = view.getActionMap();
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SPACE, 0, false), "pan.activate");
        im.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SPACE, 0, true),  "pan.deactivate");
        am.put("pan.activate",   new javax.swing.AbstractAction(){ public void actionPerformed(java.awt.event.ActionEvent e){ editor.setTool(panTool); }});
        am.put("pan.deactivate", new javax.swing.AbstractAction(){ public void actionPerformed(java.awt.event.ActionEvent e){ editor.setTool(selectionTool); }});

        // ---- B) Spacebar binding at the ROOT PANE level (macOS focus quirks)
        javax.swing.SwingUtilities.invokeLater(() -> {
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

        // ---- C) Mouse fallback: ⌥ Option + left drag OR middle-button drag pans (no spacebar needed)
        view.addMouseListener(new java.awt.event.MouseAdapter() {
            private boolean tempPan = false;
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isMiddleMouseButton(e) || e.isAltDown()) {
                    tempPan = true;
                    editor.setTool(panTool);
                }
            }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                if (tempPan) {
                    tempPan = false;
                    editor.setTool(selectionTool);
                }
            }
        });
    }

    
    /* -------- toolbar button actions (act on active tab) -------- */

    private void doZoomIn()  { withActiveZoomTarget((view, sp) -> zoomAt(view, sp, WHEEL_STEP, centerOfViewport(sp))); }
    private void doZoomOut() { withActiveZoomTarget((view, sp) -> zoomAt(view, sp, 1.0 / WHEEL_STEP, centerOfViewport(sp))); }
    private void doZoomReset(){ withActiveZoomTarget((view, sp) -> {
        double factor = 1.0 / view.getScaleFactor();
        zoomAt(view, sp, factor, centerOfViewport(sp));
    }); }

    /** Run an action if the selected tab contains a DefaultDrawingView inside a JScrollPane. */
    private void withActiveZoomTarget(java.util.function.BiConsumer<DefaultDrawingView, JScrollPane> action) {
        JScrollPane sp = currentScroller();
        DefaultDrawingView view = currentView(sp);
        if (sp != null && view != null) {
            action.accept(view, sp);
        } else {
            Toolkit.getDefaultToolkit().beep(); // active tab isn't a diagram
        }
    }

    private JScrollPane currentScroller() {
        java.awt.Component comp = tabs.getSelectedComponent();
        return (comp instanceof JScrollPane) ? (JScrollPane) comp : null;
    }
    private DefaultDrawingView currentView(JScrollPane sp) {
        if (sp == null) return null;
        java.awt.Component v = sp.getViewport().getView();
        return (v instanceof DefaultDrawingView) ? (DefaultDrawingView) v : null;
    }

    private static Point centerOfViewport(JScrollPane scroller) {
        JViewport vp = scroller.getViewport();
        Dimension ext = vp.getExtentSize();
        return new Point(ext.width / 2, ext.height / 2);
    }
    
    private static String formatTopicBaseLabel(Table base) {
        // Scoped name: Model.Topic.Class or Model.Class (model-level)
        String scoped = base.getScopedName(null);
        if (scoped == null) {
            return base.getName();
        }
        String[] p = scoped.split("\\.");
        if (p.length >= 3) {
            // has Topic
            return p[p.length - 2] + "::" + p[p.length - 1];
        }
        // model-level: show class only (no model)
        return p[p.length - 1];
    }
    

    private static ClassFigure ownerOf(Figure f) {
        if (f == null) return null;
        if (f instanceof ClassFigure) return (ClassFigure) f;
        return f.get(ClassFigure.OWNER_KEY); // tag set on children
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
            // If there’s a selected class, use its current color as initial
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
                // apply to figure under mouse if no selection
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
                if (e.isPopupTrigger()) {
                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
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
                            
                            System.err.println("********: color "  + chosen);
                            // apply to selection if present, else to this one
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
    
 // Helper: stable key for a class/structure
    private static String keyFor(ch.interlis.ili2c.metamodel.Table t) {
        return (t != null) ? t.getScopedName(null) : null; // e.g. Model.Topic.Class
    }
    
//    private static String topicScopedLabel(Table base) {
//        // Model.Topic.Class  → Topic::Class
//        String scoped = base.getScopedName(null);
//        if (scoped == null) return base.getName();
//        String[] parts = scoped.split("\\.");
//        if (parts.length >= 2) {
//            return parts[parts.length - 2] + "::" + parts[parts.length - 1];
//        }
//        return base.getName();
//    }

//    private static void addForeignBaseStub(Drawing drawing, ClassFigure subFig, Table base) {
//        // place the stub + label near the top-right of the subclass
//        Rectangle2D sb = subFig.getBounds();
//        double pad = 10;                  // gap from the class box
//        double ax  = sb.getMaxX() + pad;  // anchor x
//        double ay  = sb.getY() + 18;      // anchor y (near header)
//
//        // 1) invisible anchor the connection can attach to
//        StubAnchorFigure anchor = new StubAnchorFigure(ax, ay, 12, 10);
//        drawing.add(anchor);
//
//        // 2) human label to the right of the anchor
//        TextFigure label = new TextFigure(topicScopedLabel(base));
//        label.set(org.jhotdraw.draw.AttributeKeys.FONT_BOLD, Boolean.FALSE);
//        label.set(org.jhotdraw.draw.AttributeKeys.FONT_SIZE, 11d);
//        label.setSelectable(false);
//        label.setTransformable(false);
//        drawing.add(label);
//
//        // position label (measure current bounds to size it)
//        Rectangle2D lb = label.getBounds();
//        Point2D.Double la = new Point2D.Double(ax + 16, ay - 2);
//        Point2D.Double ll = new Point2D.Double(la.x + Math.max(40, lb.getWidth()), la.y + lb.getHeight());
//        label.setBounds(la, ll);
//
//        // 3) short generalization line from subclass → stub anchor (points to base)
//        GeneralizationFigure g = new GeneralizationFigure();
//        drawing.add(g);
//        g.setStartConnector(subFig.connector());                 // subclass
//        g.setEndConnector(new ChopRectangleConnector(anchor));   // foreign base stub
//        g.updateConnection();                                    // compute once now
//    }
    
//    static final class StubAnchorFigure extends RectangleFigure {
//        StubAnchorFigure(double x, double y, double w, double h) {
//            // invisible but connectable
//            set(org.jhotdraw.draw.AttributeKeys.FILL_COLOR,  null);
//            set(org.jhotdraw.draw.AttributeKeys.STROKE_COLOR, null);
//            set(org.jhotdraw.draw.AttributeKeys.STROKE_WIDTH, 0d);
//            setSelectable(false);
//            setTransformable(false);
//            setConnectable(true);
//            setBounds(new Point2D.Double(x, y), new Point2D.Double(x + Math.max(1, w), y + Math.max(1, h)));
//        }
//    }
}
