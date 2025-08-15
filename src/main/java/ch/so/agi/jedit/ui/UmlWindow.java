package ch.so.agi.jedit.ui;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Iterator;

// JHotDraw 7.x
import org.jhotdraw.draw.DefaultDrawing;
import org.jhotdraw.draw.DefaultDrawingEditor;
import org.jhotdraw.draw.DefaultDrawingView;
import org.jhotdraw.draw.Drawing;
import org.jhotdraw.draw.DrawingEditor;
import org.jhotdraw.draw.DrawingView;
import org.jhotdraw.draw.GroupFigure;
import org.jhotdraw.draw.RectangleFigure;
import org.jhotdraw.draw.TextAreaFigure;
import org.jhotdraw.draw.tool.CreationTool;
import org.jhotdraw.draw.tool.SelectionTool;

// INTERLIS / ili2c
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.Container;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.Viewable;
import ch.interlis.ili2c.metamodel.ViewableTransferElement;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Table;

import ch.so.agi.jedit.compile.TdCache;

public final class UmlWindow {

    // Keep UI per jEdit View
    private static final Map<View, Ui> OPEN = new WeakHashMap<>();

    private UmlWindow() {}

    /** Open (or focus) the UML window for the given jEdit View. */
    public static void show(View view) {
        SwingUtilities.invokeLater(() -> {
            Ui ui = OPEN.get(view);
            if (ui == null) {
                ui = create(view);
                OPEN.put(view, ui);
            }
            // Always (re)populate from the current buffer of this view
            populate(ui, view.getBuffer());

            if (!ui.dialog.isVisible()) ui.dialog.setVisible(true);
            ui.dialog.toFront();
            ui.dialog.requestFocus();
        });
    }

    /* ------------------------------ UI wiring ------------------------------ */

    private static Ui create(View owner) {
        JDialog dlg = new JDialog(owner, "INTERLIS – UML diagram", false);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dlg.setSize(1000, 700);
        dlg.setLocationRelativeTo(owner);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // JHotDraw editor + view
        DefaultDrawingEditor editor = new DefaultDrawingEditor();
        DefaultDrawingView view = new DefaultDrawingView();
        view.setBackground(Color.white);
        editor.add(view);
        editor.setActiveView(view);
        view.setDrawing(new DefaultDrawing());

        // View scroller
        JScrollPane scroller = new JScrollPane(view);
        content.add(scroller, BorderLayout.CENTER);

        // Tiny toolbar (optional)
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        JButton selectBtn = new JButton("Select");
        selectBtn.addActionListener(ae -> editor.setTool(new SelectionTool()));
        JButton rectBtn = new JButton("Rect");
        rectBtn.addActionListener(ae -> editor.setTool(new CreationTool(new RectangleFigure())));
        tb.add(selectBtn);
        tb.add(rectBtn);
        content.add(tb, BorderLayout.NORTH);

        dlg.setContentPane(content);

        Ui ui = new Ui(dlg, editor, view);
        dlg.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) {
                OPEN.remove(owner);
            }
        });
        return ui;
    }

    private static final class Ui {
        final JDialog dialog;
        final DrawingEditor editor;
        final DefaultDrawingView view;
        Ui(JDialog d, DrawingEditor ed, DefaultDrawingView v) {
            this.dialog = d; this.editor = ed; this.view = v;
        }
    }

    /* ---------------------------- Diagram logic ---------------------------- */

    private static void populate(Ui ui, Buffer buf) {
        // Clear drawing
        Drawing drawing = new DefaultDrawing();
        ui.view.setDrawing(drawing);

        // Get the latest *valid* TransferDescription (last successful compile)
        TransferDescription td = TdCache.peekLastValid(buf);
        if (td == null) {
            // Friendly hint if user hasn’t compiled/saved yet
            TextAreaFigure msg = new TextAreaFigure();
            msg.setText("No compiled model available.\nSave the file or run “Compile current file”.");
            Point2D.Double a = new Point2D.Double(40, 40);
            Point2D.Double b = new Point2D.Double(440, 140);
            msg.setBounds(a, b);
            drawing.add(msg);
            return;
        }

        // “Last model in the file”
        Model[] models = td.getModelsFromLastFile();
        if (models == null || models.length == 0) return;
        Model last = models[models.length - 1];

        drawClassesWithAttributes(drawing, last);
    }

    /** Draw one box per CLASS (Table with isIdentifiable()), listing its attribute names. */
    private static void drawClassesWithAttributes(Drawing drawing, Model model) {
        // Simple grid layout
        final double startX = 40, startY = 40;
        final double gapX = 40, gapY = 40;
        final int cols = 3;

        int count = 0;

        for (Iterator<?> it = model.iterator(); it.hasNext();) {
            Object o = it.next();
            if (!(o instanceof Element)) continue;
            Element e = (Element) o;

            if (e instanceof Viewable) {
                Viewable v = (Viewable) e;
                if (v instanceof Table) {
                    Table t = (Table) v;
                    if (!t.isIdentifiable()) continue; // STRUCTURE → skip, we only draw CLASS for now

                    // Collect attribute names
                    StringBuilder sb = new StringBuilder();
                    sb.append(t.getName()).append("\n");
                    sb.append("──────────────").append("\n"); // simple separator

                    for (Iterator<?> ai = t.getAttributesAndRoles2(); ai.hasNext();) {
                        ViewableTransferElement vte = (ViewableTransferElement) ai.next();
                        if (vte.obj instanceof AttributeDef) {
                            AttributeDef a = (AttributeDef) vte.obj;
                            sb.append(a.getName()).append("\n");
                        }
                    }

                    // Create a text area figure
                    TextAreaFigure text = new TextAreaFigure();
                    text.setText(sb.toString());

                    // Place into a rough grid
                    int col = count % cols;
                    int row = count / cols;
                    double x = startX + col * 280; // approx box width + gap
                    double y = startY + row * 180; // approx box height + gap

                    // First, give the text a provisional box so it can compute its preferred size
                    Point2D.Double a = new Point2D.Double(x + 10, y + 10);
                    Point2D.Double b = new Point2D.Double(x + 230, y + 200); // rough width/height
                    text.setBounds(a, b);

                    // Now size the surrounding rectangle to the text’s real bounds (+ padding)
                    Rectangle2D tb = text.getBounds();
                    double pad = 8;
                    RectangleFigure rect = new RectangleFigure(
                            tb.getX() - pad, tb.getY() - pad,
                            tb.getWidth() + 2 * pad, tb.getHeight() + 2 * pad
                    );

                    // Group so they move together
                    GroupFigure group = new GroupFigure();
                    group.add(rect);
                    group.add(text);

                    // Snap group bounds to the computed rectangle
                    group.setBounds(
                            new Point2D.Double(rect.getBounds().getX(), rect.getBounds().getY()),
                            new Point2D.Double(rect.getBounds().getMaxX(), rect.getBounds().getMaxY())
                    );

                    drawing.add(group);
                    count++;
                }
            }
        }
    }
}
