package ch.so.agi.jedit.ui;

import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.util.Log;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ch.interlis.ili2c.metamodel.*;
import ch.so.agi.jedit.compile.TdCache;

// JHotDraw 7.6
import org.jhotdraw.draw.*;
import org.jhotdraw.draw.tool.CreationTool;
import org.jhotdraw.draw.tool.SelectionTool;
// If your JHotDraw jar uses org.jhotdraw.draw.figures.*, change imports below accordingly.
import org.jhotdraw.draw.RectangleFigure;
import org.jhotdraw.draw.TextFigure;

public final class UmlDockable extends JPanel {

    private final View view;
    private final String position;

    private final JTabbedPane tabs = new JTabbedPane();
    private final JButton refreshBtn = new JButton("Refresh");

    public UmlDockable(View view, String position) {
        super(new BorderLayout(8, 8));
        this.view = view;
        this.position = position;

        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Toolbar
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.add(new JLabel("INTERLIS – UML diagram"));
        tb.add(Box.createHorizontalStrut(12));
        tb.add(refreshBtn);
        tb.add(Box.createHorizontalGlue());
        add(tb, BorderLayout.NORTH);

        add(tabs, BorderLayout.CENTER);

        refreshBtn.addActionListener(e -> rebuildFromCache());

        // initial build
        rebuildFromCache();
    }

    /** Rebuild tabs from the latest cached TD (non-blocking; shows hint if none). */
    private void rebuildFromCache() {
        Buffer buf = view.getBuffer();
        TransferDescription td = TdCache.peekLastValid(buf); // last valid run, even if buffer is dirty
        if (td == null) {
            tabs.removeAll();
            tabs.addTab("No data", msgPanel(
                    "No compiled model found.\n" +
                    "Run “Compile current file” (or save) to populate the UML."));
            return;
        }

        tabs.removeAll();
        for (Model model : td.getModelsFromLastFile()) {
            // Model-level classes (outside any TOPIC)
            List<Table> modelClasses = collectModelLevelClasses(model);
            tabs.addTab(model.getName(), modelClasses.isEmpty()
                    ? msgPanel("No model-level classes.")
                    : canvasFor(modelClasses));

            // One tab per TOPIC
            for (Topic topic : collectTopics(model)) {
                List<Table> topicClasses = collectTopicClasses(topic);
                tabs.addTab(topic.getName(), topicClasses.isEmpty()
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

    /* ============================== canvas =================================== */

    /** Creates a scrollable JHotDraw canvas and lays out class boxes in a grid. */
    private static JComponent canvasFor(List<Table> classes) {
        DefaultDrawingView view = new DefaultDrawingView();
        view.setBackground(Color.white);

        DrawingEditor editor = new DefaultDrawingEditor();
        editor.add(view);
        editor.setActiveView(view);
        editor.setTool(new SelectionTool());

        Drawing drawing = new DefaultDrawing();
        view.setDrawing(drawing);

        // simple grid layout
        int cols = Math.max(1, (int)Math.ceil(Math.sqrt(classes.size())));
        int cellW = 280;
        int cellH = 180;
        int gap   = 30;

        int i = 0;
        for (Table c : classes) {
            int row = i / cols;
            int col = i % cols;
            int x = gap + col * (cellW + gap);
            int y = gap + row * (cellH + gap);

            addClassFigure(drawing, c, x, y, cellW, cellH);
            i++;
        }

        return new JScrollPane(view);
    }

    /** Draws a class “box”: name on top, then attributes as lines of text. */
    private static void addClassFigure(Drawing drawing, Table clazz, int x, int y, int w, int h) {
        // outer rectangle
        RectangleFigure box = new RectangleFigure(x, y, w, h);
        drawing.add(box);

        // title
        String title = clazz.getName() + (clazz.isAbstract() ? " (ABSTRACT)" : "");
        TextFigure titleFig = new TextFigure(title);
        placeText(titleFig, x + 10, y + 20);
        drawing.add(titleFig);

        // attributes (names only for now)
        int ty = y + 40;
        for (Iterator<?> it = clazz.getAttributesAndRoles2(); it.hasNext();) {
            ViewableTransferElement vte = (ViewableTransferElement) it.next();
            if (vte.obj instanceof AttributeDef) {
                AttributeDef a = (AttributeDef) vte.obj;
                TextFigure attr = new TextFigure(a.getName());
                placeText(attr, x + 14, ty);
                drawing.add(attr);
                ty += 16;
                if (ty > y + h - 18) break; // keep inside box
            }
        }
    }

    private static void placeText(TextFigure tf, double left, double baseline) {
        // setBounds(anchor, lead) in 7.6 uses top-left and bottom-right points
        Point2D.Double anchor = new Point2D.Double(left, baseline - 12);
        java.awt.geom.Rectangle2D b = tf.getBounds();
        Point2D.Double lead = new Point2D.Double(anchor.x + Math.max(60, b.getWidth()),
                                                 anchor.y + Math.max(14, b.getHeight()));
        tf.setBounds(anchor, lead);
    }

    private static JComponent msgPanel(String msg) {
        JTextArea ta = new JTextArea(msg);
        ta.setEditable(false);
        ta.setOpaque(false);
        ta.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
        return new JScrollPane(ta);
    }
}
