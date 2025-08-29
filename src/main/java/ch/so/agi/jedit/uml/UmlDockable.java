package ch.so.agi.jedit.uml;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.View;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ch.interlis.ili2c.metamodel.*;
import ch.so.agi.jedit.compile.TdCache;

// JHotDraw 7.6
import org.jhotdraw.draw.DefaultDrawing;
import org.jhotdraw.draw.DefaultDrawingEditor;
import org.jhotdraw.draw.DefaultDrawingView;
import org.jhotdraw.draw.Drawing;
import org.jhotdraw.draw.DrawingEditor;
import org.jhotdraw.draw.tool.SelectionTool;

public final class UmlDockable extends JPanel {

    private final View view;
    private final String position;

    private final JTabbedPane tabs = new JTabbedPane();
    private final JButton refreshBtn = new JButton("Refresh");

    private final JButton zoomOutBtn  = new JButton("–");
    private final JButton zoomResetBtn= new JButton("100%");
    private final JButton zoomInBtn   = new JButton("+");
    
    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 4.0;
    private static final double WHEEL_STEP = 1.1; // 10% per notch (fine-tune to taste)
    
    public UmlDockable(View view, String position) {
        super(new BorderLayout(8, 8));
        this.view = view;
        this.position = position;

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
    
    /** Rebuild tabs from the latest cached TD (non-blocking; shows hint if none). */
    private void rebuildFromCache() {
        Buffer buf = view.getBuffer();
        TransferDescription td = TdCache.peekLastValid(buf);
        if (td == null) {
            tabs.removeAll();
            tabs.addTab("No data", msgPanel(
                    "No compiled model found.\n" +
                    "Run “Compile current file” (or save) to populate the UML."));
            return;
        }

        tabs.removeAll();
        Model[] models = td.getModelsFromLastFile();
        if (models == null || models.length == 0) {
            tabs.addTab("No data", msgPanel("No models in last file."));
            return;
        }

        for (Model model : models) {
            List<Topic> topics = collectTopics(model);
            List<Table> modelClasses = collectModelLevelClasses(model);

            // Single tab: topics + model-level classes together
            if (topics.isEmpty() && modelClasses.isEmpty()) {
                tabs.addTab(model.getName(), msgPanel("No topics or model-level classes."));
            } else {
                tabs.addTab(model.getName(), canvasForOverview(topics, modelClasses));
            }

            // One tab per TOPIC for its classes (kept as before)
            for (Topic topic : topics) {
                List<Table> topicClasses = collectTopicClasses(topic);
                String tabTitle = /*model.getName() + "::" +*/ topic.getName();
                tabs.addTab(tabTitle, topicClasses.isEmpty()
                        ? msgPanel("No classes in this topic.")
                        : canvasFor(topicClasses));
            }
        }
        tabs.revalidate();
        tabs.repaint();
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

    /** Tables (classes) declared directly in the model (not inside any topic). */
    private static List<Table> collectModelLevelClasses(Model model) {
        ArrayList<Table> out = new ArrayList<>();
        for (Iterator<?> it = model.iterator(); it.hasNext();) {
            Object o = it.next();
            if (o instanceof Topic) continue; // skip topics here
            if (o instanceof Table) {
                Table t = (Table) o;
                if (t.isIdentifiable()) out.add(t); // classes only, not structures
            }
        }
        return out;
    }

    /** Tables (classes) declared inside a topic. */
    private static List<Table> collectTopicClasses(Topic topic) {
        ArrayList<Table> out = new ArrayList<>();
        for (Iterator<?> it = topic.iterator(); it.hasNext();) {
            Object o = it.next();
            if (o instanceof Table) {
                Table t = (Table) o;
                if (t.isIdentifiable()) out.add(t);
            }
        }
        return out;
    }

    /* ============================== canvases ================================= */

    /** Overview canvas: topics + model-level classes in one grid. */
    private static JComponent canvasForOverview(List<Topic> topics, List<Table> classes) {
        DefaultDrawingView drawingView = new DefaultDrawingView();
        drawingView.setBackground(Color.white);

        DrawingEditor editor = new DefaultDrawingEditor();
        editor.add(drawingView);
        editor.setActiveView(drawingView);

        Drawing drawing = new DefaultDrawing();
        drawingView.setDrawing(drawing);
        
        int total = topics.size() + classes.size();
        if (total == 0) return new JScrollPane(drawingView);

        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(total)));
        int cellW = 320; // a bit larger to accommodate TopicFigure body
        int cellH = 200;
        int gap   = 30;

        int i = 0;

        // Place topics first
        for (Topic t : topics) {
            int row = i / cols;
            int col = i % cols;
            int x = gap + col * (cellW + gap);
            int y = gap + row * (cellH + gap);
            addTopicFigure(drawing, t, x, y);
            i++;
        }

        // Then model-level classes
        for (Table c : classes) {
            int row = i / cols;
            int col = i % cols;
            int x = gap + col * (cellW + gap);
            int y = gap + row * (cellH + gap);
            addClassFigure(drawing, c, x, y);
            i++;
        }

        JScrollPane sp = new JScrollPane(drawingView);
        installWheelZoom(drawingView, sp);     // your existing zoom helper
        installPanSupport(drawingView, editor, sp); // <-- add this
        return sp;
    }

    /** Creates a scrollable JHotDraw canvas and lays out class boxes in a grid. */
    private static JComponent canvasFor(List<Table> classes) {
        DefaultDrawingView drawingView = new DefaultDrawingView();
        drawingView.setBackground(Color.white);

        DrawingEditor editor = new DefaultDrawingEditor();
        editor.add(drawingView);
        editor.setActiveView(drawingView);

        Drawing drawing = new DefaultDrawing();
        drawingView.setDrawing(drawing);
        
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

            addClassFigure(drawing, c, x, y);
            i++;
        }

        JScrollPane sp = new JScrollPane(drawingView);
        installWheelZoom(drawingView, sp);     // your existing zoom helper
        installPanSupport(drawingView, editor, sp); // <-- add this
        return sp;
    }

    /** Adds a TopicFigure (UML package) to the drawing at (x,y). */
    private static void addTopicFigure(Drawing drawing, Topic topic, int x, int y) {
        TopicFigure tf = new TopicFigure(topic);
        drawing.add(tf);

        // Initial anchor; TopicFigure computes its own size in layout()
        tf.setBounds(new Point2D.Double(x, y), new Point2D.Double(x + 200, y + 140));
        tf.layout(); // ensure natural size is applied
    }

    /** Adds a ClassFigure to the drawing at (x,y). */
    private static void addClassFigure(Drawing drawing, Table clazz, int x, int y) {
        ClassFigure cf = new ClassFigure(clazz);
        drawing.add(cf);

        // Initial anchor; ClassFigure computes size in layout()
        cf.setBounds(new Point2D.Double(x, y), new Point2D.Double(x + 200, y + 120));
        cf.layout(); // ensure natural size is applied
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
}
