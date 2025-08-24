package ch.so.agi.jedit.uml;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.List;

import ch.interlis.ili2c.metamodel.*;

import org.jhotdraw.draw.AttributeKeys;
import org.jhotdraw.draw.CompositeFigure;
import org.jhotdraw.draw.LineFigure;
import org.jhotdraw.draw.ListFigure;
import org.jhotdraw.draw.RectangleFigure;
import org.jhotdraw.draw.TextFigure;
import org.jhotdraw.draw.layouter.VerticalLayouter;
import org.jhotdraw.geom.Insets2D;

/** UML-style class box: title + separator + rows (attributes/roles). */
public class ClassFigure extends ListFigure {
    private final RectangleFigure background;
    private final TextFigure title;

    public ClassFigure(Table clazz) {
        this(clazz.getName() + (clazz.isAbstract() ? " (ABSTRACT)" : ""),
             collectRows(clazz));
    }

    /** Generic constructor with explicit title and row strings. */
    public ClassFigure(String titleText, List<String> rows) {
        // Provide presentation figure via super-ctor:
        super(new RectangleFigure());
        background = (RectangleFigure) getPresentationFigure();
        //background.set(AttributeKeys.CORNER_RADIUS, 8d);
        background.set(AttributeKeys.STROKE_WIDTH, 1.2d);
        background.set(AttributeKeys.FILL_COLOR, Color.white);

        // Layouter + padding (no gap param in 7.6)
        setLayouter(new VerticalLayouter());
        set(AttributeKeys.LAYOUT_INSETS, new Insets2D.Double(10, 14, 12, 14));
        
        // Title
        title = new TextFigure(titleText);
        title.set(AttributeKeys.FONT_BOLD, Boolean.FALSE);
        title.set(AttributeKeys.FONT_SIZE, 12d);
        add(title);
        add(spacer(3));

        // Separator
        LineFigure sep = new LineFigure();
        sep.set(AttributeKeys.STROKE_WIDTH, 1d);
        // give it a reasonable initial width; layouter will stretch as needed
        sep.setBounds(new Point2D.Double(0, 0), new Point2D.Double(240, 0));
        add(sep);

        // Rows
        for (String row : rows) {
            TextFigure tf = new TextFigure(row);
            tf.set(AttributeKeys.FONT_SIZE, 12d);
            add(spacer(3));
            add(tf);
        }
    }

    public void setTitle(String txt) {
        title.setText(txt);
        invalidate();
    }
    
    private static RectangleFigure spacer(int height) {
        RectangleFigure s = new RectangleFigure();
        s.set(AttributeKeys.FILL_COLOR, Color.blue);
        s.set(AttributeKeys.STROKE_WIDTH, 1d);
        s.setBounds(new Point2D.Double(0, 0),
                    new Point2D.Double(1, height)); // tiny width, specified height
        return s;
    }

    /* ===== helpers to collect row strings from an INTERLIS Table ===== */
    private static List<String> collectRows(Table clazz) {
        java.util.ArrayList<String> rows = new java.util.ArrayList<>();
        for (java.util.Iterator<?> it = clazz.getAttributesAndRoles2(); it.hasNext();) {
            ViewableTransferElement vte = (ViewableTransferElement) it.next();
            Object o = vte.obj;
            if (o instanceof AttributeDef) rows.add(formatAttribute((AttributeDef) o));
            else if (o instanceof RoleDef) {
                //rows.add(formatRole((RoleDef) o));
            }
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

//    private static String formatRole(RoleDef r) {
//        String tgt = (r.getTarget() != null) ? r.getTarget().getName() : "?";
//        String mult = (r.getCardinality() != null) ? r.getCardinality().toString() : "";
//        return r.getName() + " : " + tgt + (mult.isEmpty() ? "" : " [" + mult + "]");
//    }
}
