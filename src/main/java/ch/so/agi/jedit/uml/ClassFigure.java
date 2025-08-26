package ch.so.agi.jedit.uml;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ch.interlis.ili2c.metamodel.*;

import org.jhotdraw.draw.GraphicalCompositeFigure;
import org.jhotdraw.draw.RectangleFigure;
import org.jhotdraw.draw.TextFigure;

/**
 * JHotDraw 7.6 ClassFigure:
 * - Outer rectangle (uniform stroke, no rounded corners)
 * - Header with optional «Abstract» line + class name; bottom border is the separator (flush)
 * - Content compartment (attributes/roles) with padding and row gaps
 * - Auto-sizes to content; origin preserved during layout
 */
public class ClassFigure extends GraphicalCompositeFigure {
    /* ===== styling ===== */
    private static final double STROKE            = 1.0;  // uniform line width

    // Outer padding around everything (inside outer rectangle)
    private static final double PAD_L = 0;
    private static final double PAD_R = 0;
    private static final double PAD_T = 0;
    private static final double PAD_B = 8;

    // Header internal padding
    private static final double HPAD_L = 10;
    private static final double HPAD_R = 10;
    private static final double HPAD_T = 6;
    private static final double HPAD_B = 6;
    private static final double ABSTRACT_GAP = 2; // gap between «Abstract» and name

    // Gap between header and first row
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
    private TextFigure            abstractTf  = null;                   // shown only if class is abstract
    private final List<TextFigure> rowFigs    = new ArrayList<>();

    public ClassFigure(Table clazz) {
        // no "(ABSTRACT)" suffix in the name
        this(clazz.getName(), collectRows(clazz));
        if (clazz.isAbstract()) {
            abstractTf = new TextFigure("«Abstract»");
            abstractTf.set(org.jhotdraw.draw.AttributeKeys.FONT_BOLD, Boolean.FALSE);
            abstractTf.set(org.jhotdraw.draw.AttributeKeys.FONT_SIZE, 12d);
            add(abstractTf);
        }
    }

    public ClassFigure(String titleText, List<String> rows) {
        /* outer presentation */
        setPresentationFigure(outerRect);
        outerRect.set(org.jhotdraw.draw.AttributeKeys.STROKE_WIDTH, STROKE);
        outerRect.set(org.jhotdraw.draw.AttributeKeys.FILL_COLOR, Color.white);

        /* header rectangle: only border, no fill */
        headerRect.set(org.jhotdraw.draw.AttributeKeys.STROKE_WIDTH, STROKE);
        headerRect.set(org.jhotdraw.draw.AttributeKeys.FILL_COLOR, null);
        add(headerRect); // behind the header text

        /* title (class name) */
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
        // Preserve origin so dragging doesn’t snap to (0,0)
        Rectangle2D old = outerRect.getBounds();
        double ox = old.getX();
        double oy = old.getY();

        // --- measure header lines ---
        Rectangle2D tb = titleTf.getBounds();
        double tW = Math.max(1, tb.getWidth());
        double tH = Math.max(1, tb.getHeight());

        double aW = 0, aH = 0;
        if (abstractTf != null) {
            Rectangle2D ab = abstractTf.getBounds();
            aW = Math.max(1, ab.getWidth());
            aH = Math.max(1, ab.getHeight());
        }

        // header width must accommodate the widest of the two header lines
        double headerInnerW = HPAD_L + Math.max(tW, aW) + HPAD_R;

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

        double rowsInnerW = CPAD_L + maxRowW + CPAD_R;
        double innerW     = Math.max(headerInnerW, rowsInnerW);
        double totalW     = PAD_L + innerW + PAD_R;

        double y = PAD_T;

        // header height = padding + (optional abstract line) + (gap) + title line + padding
        double headerH = HPAD_T
                + (abstractTf != null ? aH + ABSTRACT_GAP : 0)
                + tH
                + HPAD_B;

        // --- place header rect (flush left/right inside outer rect) ---
        headerRect.setBounds(
            new Point2D.Double(ox + PAD_L,        oy + y),
            new Point2D.Double(ox + PAD_L + innerW, oy + y + headerH)
        );

        // --- place «Abstract» line (if any) ---
        double textY = oy + y + HPAD_T;
        if (abstractTf != null) {
            Point2D.Double aA = new Point2D.Double(ox + PAD_L + HPAD_L, textY);
            Point2D.Double aL = new Point2D.Double(aA.x + aW, aA.y + aH);
            abstractTf.setBounds(aA, aL);
            textY += aH + ABSTRACT_GAP;
        }

        // --- place class name under it ---
        Point2D.Double tA = new Point2D.Double(ox + PAD_L + HPAD_L, textY);
        Point2D.Double tL = new Point2D.Double(tA.x + tW,           tA.y + tH);
        titleTf.setBounds(tA, tL);

        y += headerH + AFTER_HEADER_GAP;

        // --- rows (offset by origin) ---
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

        // Outer rect keeps origin, grows to enclose everything
        outerRect.setBounds(
            new Point2D.Double(ox, oy),
            new Point2D.Double(ox + totalW, oy + totalH)
        );
    }

    /* ===== helpers ===== */

    private static List<String> collectRows(Table clazz) {
        ArrayList<String> rows = new ArrayList<>();
        for (Iterator<?> it = clazz.getAttributesAndRoles2(); it.hasNext();) {
            ViewableTransferElement vte = (ViewableTransferElement) it.next();
            Object o = vte.obj;
            if (o instanceof AttributeDef) {
                AttributeDef attrDef = ((AttributeDef) o);
//                attrDef.i
                rows.add(formatAttribute((AttributeDef) o));
            }
            // Add RoleDef later if you want association ends in this compartment
            // else if (o instanceof RoleDef) rows.add(formatRole((RoleDef) o));
        }
        return rows;
    }

    private static String formatAttribute(AttributeDef a) {
        String typeName = "?";
        Type t = a.getDomainResolvingAliases();
        if (t != null) {
            if (t instanceof ch.interlis.ili2c.metamodel.TextType) {
                typeName = "String";
            } else if (t instanceof ch.interlis.ili2c.metamodel.NumericType) {
                typeName = "Numeric";
            } else if (t instanceof ch.interlis.ili2c.metamodel.SurfaceType) {
                typeName = "Surface";
            } else if (t instanceof ch.interlis.ili2c.metamodel.AreaType) {
                typeName = "Area";
            } else if (t instanceof ch.interlis.ili2c.metamodel.CoordType) {
                CoordType ct = (CoordType) t;
                NumericalType[] nts = ((CoordType) t).getDimensions();
                typeName = "Coord" + nts.length;
            } else if (t instanceof ch.interlis.ili2c.metamodel.EnumerationType) {
                typeName = a.isDomainBoolean() ? "Boolean" : a.getContainer().getName();
            } else if (t instanceof ch.interlis.ili2c.metamodel.CompositionType) {
                typeName = ((CompositionType) t).getComponentType().getName();
            }
        }
        String mult = (a.getCardinality() != null) ? a.getCardinality().toString() : "";
        return a.getName()
                + (mult.isEmpty() ? "" : mult.replace("{", "[").replace("}", "]"))
                + " : " + typeName;
    }
}
