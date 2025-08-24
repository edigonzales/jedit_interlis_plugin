package ch.so.agi.jedit.uml;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import ch.interlis.ili2c.metamodel.*;

import org.jhotdraw.draw.GraphicalCompositeFigure;
import org.jhotdraw.draw.RectangleFigure;
import org.jhotdraw.draw.TextFigure;

/**
 * JHotDraw 7.6 ClassFigure:
 * - Outer rectangle (uniform stroke, no rounded corners)
 * - Title compartment with its own border; its BOTTOM border is the separator (flush)
 * - Content compartment (attributes/roles) with padding and row gaps
 * - Auto-sizes to content; no dependency on VerticalLayouter/ListFigure stretching
 */
public class ClassFigure extends GraphicalCompositeFigure {
    /* ===== styling ===== */
    private static final double STROKE            = 1.0;  // uniform line width

    // Outer padding around everything (inside outer rectangle)
    private static final double PAD_L = 0;
    private static final double PAD_R = 0;
    private static final double PAD_T = 0;
    private static final double PAD_B = 8;

    // Title band internal padding
    private static final double HPAD_L = 10;
    private static final double HPAD_R = 10;
    private static final double HPAD_T = 6;
    private static final double HPAD_B = 6;

    // Gap between title band and first row
    private static final double AFTER_HEADER_GAP = 6;

    // Content (rows) internal padding (left/right only; top is AFTER_HEADER_GAP)
    private static final double CPAD_L = 12;
    private static final double CPAD_R = 12;

    // Vertical gap between rows
    private static final double ROW_GAP = 4;

    /* ===== figures ===== */
    private final RectangleFigure outerRect   = new RectangleFigure();  // presentation of this composite
    private final RectangleFigure headerRect  = new RectangleFigure();  // draws the header border (separator = bottom stroke)
    private final TextFigure      titleTf     = new TextFigure();
    private final List<TextFigure> rowFigs    = new ArrayList<>();

    public ClassFigure(Table clazz) {
        this(clazz.getName() + (clazz.isAbstract() ? " (ABSTRACT)" : ""), collectRows(clazz));
    }

    public ClassFigure(String titleText, List<String> rows) {
        /* outer presentation */
        setPresentationFigure(outerRect);
        outerRect.set(org.jhotdraw.draw.AttributeKeys.STROKE_WIDTH, STROKE);
        outerRect.set(org.jhotdraw.draw.AttributeKeys.FILL_COLOR, Color.white);

        /* header rectangle: only border, no fill */
        headerRect.set(org.jhotdraw.draw.AttributeKeys.STROKE_WIDTH, STROKE);
        headerRect.set(org.jhotdraw.draw.AttributeKeys.FILL_COLOR, null);
        add(headerRect); // behind the title text

        /* title */
        titleTf.setText(titleText);
        titleTf.set(org.jhotdraw.draw.AttributeKeys.FONT_BOLD, Boolean.FALSE);
        titleTf.set(org.jhotdraw.draw.AttributeKeys.FONT_SIZE, 12d);
        add(titleTf);

        /* rows (attributes/roles) */
        for (String row : rows) {
            TextFigure tf = new TextFigure(row);
            tf.set(org.jhotdraw.draw.AttributeKeys.FONT_SIZE, 12d);
            rowFigs.add(tf);
            add(tf);
        }
    }

    public void setTitle(String txt) {
        titleTf.setText(txt);
        layout(); // recompute immediately
    }

    @Override
    public void layout() {
        // ▲ Keep current origin so layout doesn't teleport to (0,0)
        Rectangle2D old = outerRect.getBounds();
        double ox = old.getX();
        double oy = old.getY();

        // --- measure title ---
        Rectangle2D tb = titleTf.getBounds();
        double tW = Math.max(1, tb.getWidth());
        double tH = Math.max(1, tb.getHeight());

        // --- measure rows ---
        double maxRowW = 0;
        double rowsH   = 0;
        for (int i = 0; i < rowFigs.size(); i++) {
            Rectangle2D rb = rowFigs.get(i).getBounds();
            double rW = Math.max(1, rb.getWidth());
            double rH = Math.max(1, rb.getHeight());
            maxRowW = Math.max(maxRowW, rW);
            rowsH  += rH;
            if (i < rowFigs.size() - 1) rowsH += ROW_GAP;
        }

        double headerInnerW = HPAD_L + tW      + HPAD_R;
        double rowsInnerW   = CPAD_L + maxRowW + CPAD_R;
        double innerW       = Math.max(headerInnerW, rowsInnerW);

        double totalW = PAD_L + innerW + PAD_R;

        double y = PAD_T;
        double headerH = HPAD_T + tH + HPAD_B;

        // ▲ Place header at current origin + padding
        headerRect.setBounds(
            new Point2D.Double(ox + PAD_L,        oy + y),
            new Point2D.Double(ox + PAD_L+innerW, oy + y + headerH)
        );

        // ▲ Title inside header (offset by origin)
        Point2D.Double tA = new Point2D.Double(ox + PAD_L + HPAD_L, oy + y + HPAD_T);
        Point2D.Double tL = new Point2D.Double(tA.x + tW,           tA.y + tH);
        titleTf.setBounds(tA, tL);

        y += headerH + AFTER_HEADER_GAP;

        // ▲ Rows (offset by origin)
        double rx = ox + PAD_L + CPAD_L;
        for (int i = 0; i < rowFigs.size(); i++) {
            TextFigure tf = rowFigs.get(i);
            Rectangle2D rb = tf.getBounds();
            double rW = Math.max(1, rb.getWidth());
            double rH = Math.max(1, rb.getHeight());

            Point2D.Double a = new Point2D.Double(rx, oy + y);
            Point2D.Double l = new Point2D.Double(a.x + rW, a.y + rH);
            tf.setBounds(a, l);

            y += rH;
            if (i < rowFigs.size() - 1) y += ROW_GAP;
        }

        double totalH = Math.max(y + PAD_B, headerH + PAD_T + PAD_B);

        // ▲ Keep origin; only change size
        outerRect.setBounds(
            new Point2D.Double(ox, oy),
            new Point2D.Double(ox + totalW, oy + totalH)
        );
    }

//    @Override
//    public void layout() {
//        // --- measure title ---
//        Rectangle2D tb = titleTf.getBounds();
//        double tW = Math.max(1, tb.getWidth());
//        double tH = Math.max(1, tb.getHeight());
//
//        // --- measure rows ---
//        double maxRowW = 0;
//        double rowsH   = 0;
//        for (int i = 0; i < rowFigs.size(); i++) {
//            TextFigure tf = rowFigs.get(i);
//            Rectangle2D rb = tf.getBounds();
//            double rW = Math.max(1, rb.getWidth());
//            double rH = Math.max(1, rb.getHeight());
//            maxRowW = Math.max(maxRowW, rW);
//            rowsH  += rH;
//            if (i < rowFigs.size() - 1) rowsH += ROW_GAP;
//        }
//
//        // --- compute inner width needed by title vs content ---
//        double headerInnerW = HPAD_L + tW       + HPAD_R;
//        double rowsInnerW   = CPAD_L + maxRowW  + CPAD_R;
//        double innerW       = Math.max(headerInnerW, rowsInnerW);
//
//        // total figure size
//        double totalW = PAD_L + innerW + PAD_R;
//
//        // header band height and start y
//        double y      = PAD_T;
//        double headerH = HPAD_T + tH + HPAD_B;
//
//        // --- place header rect full-width (flush left/right inside the outer rect) ---
//        headerRect.setBounds(
//                new Point2D.Double(PAD_L, y),
//                new Point2D.Double(PAD_L + innerW, y + headerH)
//        );
//
//        // --- place title inside header ---
//        Point2D.Double tA = new Point2D.Double(PAD_L + HPAD_L, y + HPAD_T);
//        Point2D.Double tL = new Point2D.Double(tA.x + tW, tA.y + tH);
//        titleTf.setBounds(tA, tL);
//
//        // --- rows below header, with gap ---
//        y += headerH + AFTER_HEADER_GAP;
//
//        double rx = PAD_L + CPAD_L;
//        for (int i = 0; i < rowFigs.size(); i++) {
//            TextFigure tf = rowFigs.get(i);
//            Rectangle2D rb = tf.getBounds();
//            double rW = Math.max(1, rb.getWidth());
//            double rH = Math.max(1, rb.getHeight());
//
//            Point2D.Double a = new Point2D.Double(rx, y);
//            Point2D.Double l = new Point2D.Double(a.x + rW, a.y + rH);
//            tf.setBounds(a, l);
//
//            y += rH;
//            if (i < rowFigs.size() - 1) y += ROW_GAP;
//        }
//
//        double totalH = Math.max(y + PAD_B, headerH + PAD_T + PAD_B); // guards empty rows
//
//        // --- set the outer rect to enclose everything exactly ---
//        outerRect.setBounds(
//                new Point2D.Double(0, 0),
//                new Point2D.Double(totalW, totalH)
//        );
//    }

    /* ===== helpers ===== */

    private static List<String> collectRows(Table clazz) {
        ArrayList<String> rows = new ArrayList<>();
        for (java.util.Iterator<?> it = clazz.getAttributesAndRoles2(); it.hasNext();) {
            ViewableTransferElement vte = (ViewableTransferElement) it.next();
            Object o = vte.obj;
            if (o instanceof AttributeDef) rows.add(formatAttribute((AttributeDef) o));
            // Add RoleDef here later if/when you want association ends in this compartment
            // else if (o instanceof RoleDef) rows.add(formatRole((RoleDef) o));
        }
        return rows;
    }

    private static String formatAttribute(AttributeDef a) {
        String typeName = "?";
        Type t = a.getDomain();
        if (t != null) {
            if (t.getName() != null) typeName = t.getName();
            else if (t.getContainer() != null) typeName = t.toString();
        }
        String mult = (a.getCardinality() != null) ? a.getCardinality().toString() : "";
        return a.getName() + " : " + typeName + (mult.isEmpty() ? "" : " [" + mult + "]");
    }
}
