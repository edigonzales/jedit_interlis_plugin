package ch.so.agi.jedit.uml;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.View;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D.Double;
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

        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        add(tabs, BorderLayout.CENTER);

        refreshBtn.addActionListener(e -> rebuildFromCache());

        // initial build
        rebuildFromCache();
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
            // Model-level classes (outside any TOPIC)
            List<Table> modelClasses = collectModelLevelClasses(model);
            tabs.addTab(model.getName(), modelClasses.isEmpty()
                    ? msgPanel("No model-level classes.")
                    : canvasFor(modelClasses));

            // One tab per TOPIC
            for (Topic topic : collectTopics(model)) {
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

    /* ============================== canvas =================================== */

    /** Creates a scrollable JHotDraw canvas and lays out class boxes in a grid. */
    private static JComponent canvasFor(List<Table> classes) {
        DefaultDrawingView drawingView = new DefaultDrawingView();
        drawingView.setBackground(Color.white);

        DrawingEditor editor = new DefaultDrawingEditor();
        editor.add(drawingView);
        editor.setActiveView(drawingView);
        editor.setTool(new SelectionTool());

        Drawing drawing = new DefaultDrawing();
        drawingView.setDrawing(drawing);

        // simple grid layout
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

        return new JScrollPane(drawingView);
    }

    /** Adds a ClassFigure to the drawing at (x,y). */
    private static void addClassFigure(Drawing drawing, Table clazz, int x, int y) {
        ClassFigure cf = new ClassFigure(clazz);
        drawing.add(cf);

        // Compute natural size, then position at (x,y)
        cf.layout(); // passiert automatisch
        Double b = cf.getBounds();
        cf.setBounds(new Point2D.Double(x, y),
                     new Point2D.Double(x + b.getWidth(), y + b.getHeight()));
    }
    
    private static JComponent msgPanel(String msg) {
        JTextArea ta = new JTextArea(msg);
        ta.setEditable(false);
        ta.setOpaque(false);
        ta.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
        return new JScrollPane(ta);
    }
}
