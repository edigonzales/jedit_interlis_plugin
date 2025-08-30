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
import org.jhotdraw.draw.connector.ChopRectangleConnector;
import org.jhotdraw.draw.connector.Connector;

/**
 * JHotDraw 7.6 ClassFigure:
 * - Header with optional «Structure» and/or «Abstract» lines + class name
 * - Bottom border of header acts as separator
 * - Auto-sizes to content; origin preserved during layout
 */
public class ClassFigure extends GraphicalCompositeFigure {
    /* ===== styling ===== */
    private static final double STROKE            = 1.0;

    private static final double PAD_L = 0;
    private static final double PAD_R = 0;
    private static final double PAD_T = 0;
    private static final double PAD_B = 8;

    private static final double HPAD_L = 10;
    private static final double HPAD_R = 10;
    private static final double HPAD_T = 6;
    private static final double HPAD_B = 6;
    private static final double ABSTRACT_GAP = 2; // also used between stereotype lines

    private static final double AFTER_HEADER_GAP = 6;

    private static final double CPAD_L = 12;
    private static final double CPAD_R = 12;

    private static final double ROW_GAP = 4;

    /* ===== figures ===== */
    private final RectangleFigure outerRect   = new RectangleFigure();
    private final RectangleFigure headerRect  = new RectangleFigure();
    private final TextFigure      titleTf     = new TextFigure();
    private TextFigure            abstractTf  = null;   // optional
    private TextFigure            structureTf = null;   // optional
    private TextFigure extendsTf = null;
    private final List<TextFigure> rowFigs    = new ArrayList<>();

    public ClassFigure(Table clazz) {
        this(clazz.getName(), collectRows(clazz));
        if (clazz.isAbstract()) {
            abstractTf = new TextFigure("«Abstract»");
            abstractTf.set(org.jhotdraw.draw.AttributeKeys.FONT_BOLD, Boolean.FALSE);
            abstractTf.set(org.jhotdraw.draw.AttributeKeys.FONT_SIZE, 12d);
            add(abstractTf);
        }
        if (!clazz.isIdentifiable()) {
            structureTf = new TextFigure("«Structure»");
            structureTf.set(org.jhotdraw.draw.AttributeKeys.FONT_BOLD, Boolean.FALSE);
            structureTf.set(org.jhotdraw.draw.AttributeKeys.FONT_SIZE, 12d);
            add(structureTf);
        }
    }

    public ClassFigure(String titleText, List<String> rows) {
     // Make the composite the thing that connections and users interact with
        setConnectable(true);
        setSelectable(true);

        // Children should not be individually selectable/transformable/connectable
        outerRect.setSelectable(false);
        outerRect.setTransformable(false);
        outerRect.setConnectable(false);

        titleTf.setSelectable(false);
        titleTf.setTransformable(false);
        if (abstractTf != null) {
            abstractTf.setSelectable(false);
            abstractTf.setTransformable(false);
        }
        if (structureTf != null) {
            structureTf.setSelectable(false);
            structureTf.setTransformable(false);
        }
        for (TextFigure tf : rowFigs) {
            tf.setSelectable(false);
            tf.setTransformable(false);
        }
        
        setPresentationFigure(outerRect);
        outerRect.set(org.jhotdraw.draw.AttributeKeys.STROKE_WIDTH, STROKE);
        outerRect.set(org.jhotdraw.draw.AttributeKeys.FILL_COLOR, Color.white);
        
        headerRect.set(org.jhotdraw.draw.AttributeKeys.STROKE_WIDTH, STROKE);
        headerRect.set(org.jhotdraw.draw.AttributeKeys.FILL_COLOR, null);
        add(headerRect);

        titleTf.setText(titleText);
        titleTf.set(org.jhotdraw.draw.AttributeKeys.FONT_BOLD, Boolean.FALSE);
        titleTf.set(org.jhotdraw.draw.AttributeKeys.FONT_SIZE, 12d);
        add(titleTf);

        for (String row : rows) {
            TextFigure tf = new TextFigure(row);
            tf.set(org.jhotdraw.draw.AttributeKeys.FONT_SIZE, 12d);
            rowFigs.add(tf);
            add(tf);
        }
    }

    public void setTitle(String txt) {
        titleTf.setText(txt);
        layout();
    }
    
    public Connector connector() {
        // outerRect is your presentation RectangleFigure
        return new ChopRectangleConnector(this);
    }
    
    @Override
    public Connector findConnector(java.awt.geom.Point2D.Double p, org.jhotdraw.draw.ConnectionFigure cf) {
        return new ChopRectangleConnector(this);
    }

    @Override
    public void layout() {
        Rectangle2D old = outerRect.getBounds();
        double ox = old.getX();
        double oy = old.getY();

        // --- measure header parts ---
        Rectangle2D tb = titleTf.getBounds();
        double tW = Math.max(1, tb.getWidth());
        double tH = Math.max(1, tb.getHeight());

        double aW = 0, aH = 0;
        if (abstractTf != null) {
            Rectangle2D ab = abstractTf.getBounds();
            aW = Math.max(1, ab.getWidth());
            aH = Math.max(1, ab.getHeight());
        }

        double sW = 0, sH = 0;
        if (structureTf != null) {
            Rectangle2D sb = structureTf.getBounds();
            sW = Math.max(1, sb.getWidth());
            sH = Math.max(1, sb.getHeight());
        }
        
        double eW = 0, eH = 0;
        if (extendsTf != null) {
            Rectangle2D eb = extendsTf.getBounds();
            eW = Math.max(1, eb.getWidth());
            eH = Math.max(1, eb.getHeight());
        }

        double headerInnerW = HPAD_L + Math.max(Math.max(tW, Math.max(aW, sW)), eW) + HPAD_R;
        
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

        // header height: padding + (structure?) + (gap if structure present and then maybe abstract)
        //               + (abstract?) + (gap if abstract present) + title + padding
        double headerH = HPAD_T
                + (structureTf != null ? sH + ABSTRACT_GAP : 0)
                + (abstractTf  != null ? aH + ABSTRACT_GAP : 0)
                + (extendsTf   != null ? eH + ABSTRACT_GAP : 0)
                + tH
                + HPAD_B;
        
        // header rectangle
        headerRect.setBounds(
            new Point2D.Double(ox + PAD_L,          oy + y),
            new Point2D.Double(ox + PAD_L + innerW, oy + y + headerH)
        );

        // place lines inside header (top to bottom)
        double textY = oy + y + HPAD_T;
        
        if (structureTf != null) {
            Point2D.Double sA = new Point2D.Double(ox + PAD_L + HPAD_L, textY);
            Point2D.Double sL = new Point2D.Double(sA.x + sW,           sA.y + sH);
            structureTf.setBounds(sA, sL);
            textY += sH + ABSTRACT_GAP;
        }

        if (abstractTf != null) {
            Point2D.Double aA = new Point2D.Double(ox + PAD_L + HPAD_L, textY);
            Point2D.Double aL = new Point2D.Double(aA.x + aW,           aA.y + aH);
            abstractTf.setBounds(aA, aL);
            textY += aH + ABSTRACT_GAP;
        }
        
        if (extendsTf   != null) {
            Point2D.Double eA = new Point2D.Double(ox + PAD_L + HPAD_L, textY);
            Point2D.Double eL = new Point2D.Double(eA.x + eW,           eA.y + eH);
            extendsTf.setBounds(eA, eL);
            textY += eH + ABSTRACT_GAP;
        }

        Point2D.Double tA = new Point2D.Double(ox + PAD_L + HPAD_L, textY);
        Point2D.Double tL = new Point2D.Double(tA.x + tW,           tA.y + tH);
        titleTf.setBounds(tA, tL);

        y += headerH + AFTER_HEADER_GAP;

        // rows
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

        outerRect.setBounds(
            new Point2D.Double(ox, oy),
            new Point2D.Double(ox + totalW, oy + totalH)
        );
    }
    
    public void setForeignBaseLabel(String labelOrNull) {        
        if (labelOrNull == null || labelOrNull.trim().isEmpty()) {
            if (extendsTf != null) {
                remove(extendsTf);
                extendsTf = null;
                layout();
            }
            return;
        }
        if (extendsTf == null) {
            extendsTf = new TextFigure();
            extendsTf.set(org.jhotdraw.draw.AttributeKeys.FONT_BOLD, Boolean.FALSE);
            extendsTf.set(org.jhotdraw.draw.AttributeKeys.FONT_SIZE, 12d);
            extendsTf.setText("«extends " + labelOrNull + "»");
            add(extendsTf);
        }
        layout();
    }

    /* ===== helpers (unchanged) ===== */

    private static List<String> collectRows(Table clazz) {
        ArrayList<String> rows = new ArrayList<>();
        for (java.util.Iterator<?> it = clazz.getAttributesAndRoles2(); it.hasNext();) {
            ViewableTransferElement vte = (ViewableTransferElement) it.next();
            Object o = vte.obj;
            if (o instanceof AttributeDef) {
                AttributeDef a = (AttributeDef) o;

                AttrKind kind = classify(clazz, a);
                String label  = formatAttribute(a);

                switch (kind) {
                    case INHERITED: {
                        String baseDeclScoped = declaringClassScopedName(root(a));
                        //rows.add(label + "  «from " + shortName(baseDeclScoped) + "»");
                        break;
                    }
                    case OVERRIDES: {
                        AttributeDef base = (AttributeDef) a.getExtending();
                        String baseDeclScoped = declaringClassScopedName(root(base));
                        //rows.add(label + "  «overrides " + shortName(baseDeclScoped) + "." + base.getName() + "»");
                        rows.add(label);
                        break;
                    }
                    default:
                        rows.add(label);
                }
            }
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

    /* ===== inheritance helpers (unchanged) ===== */

    enum AttrKind { DECLARED_HERE, INHERITED, OVERRIDES }

    private static String declaringClassScopedName(AttributeDef a) {
        Container<?> c = a.getContainer(Viewable.class);
        return (c != null) ? c.getScopedName(null) : null;
    }

    private static String shortName(String scoped) {
        if (scoped == null) return "?";
        int i = scoped.lastIndexOf('.');
        return (i >= 0) ? scoped.substring(i + 1) : scoped;
    }

    private static boolean isSuperclassOf(Viewable owner, String otherScoped) {
        if (otherScoped == null) return false;
        Element cur = owner;
        while (cur instanceof Extendable) {
            Element p = ((Extendable) cur).getExtending();
            if (!(p instanceof Viewable)) break;
            String scoped = ((Viewable) p).getScopedName(null);
            if (otherScoped.equals(scoped)) return true;
            cur = p;
        }
        return false;
    }

    private static AttributeDef root(AttributeDef a) {
        AttributeDef r = a.getRootExtending();
        return (r != null) ? r : a;
    }

    private static AttrKind classify(Table owner, AttributeDef attr) {
        String ownerScoped = owner.getScopedName(null);
        String declScoped  = declaringClassScopedName(attr);

        if (!ownerScoped.equals(declScoped)) {
            return isSuperclassOf(owner, declScoped) ? AttrKind.INHERITED : AttrKind.DECLARED_HERE;
        }
        Element ext = attr.getExtending();
        if (ext instanceof AttributeDef) {
            AttributeDef base = (AttributeDef) ext;
            String baseDeclScoped = declaringClassScopedName(base);
            if (isSuperclassOf(owner, baseDeclScoped)) return AttrKind.OVERRIDES;
        }
        return AttrKind.DECLARED_HERE;
    }

    private static boolean isDerivedFromSuperclass(Viewable owner, AttributeDef attr) {
        String declaredHereScoped = declaringClassScopedName(attr);
        String ownerScoped        = owner.getScopedName(null);

        if (!ownerScoped.equals(declaredHereScoped)) {
            return isSuperclassOf(owner, declaredHereScoped);
        }
        Element ext = attr.getExtending();
        if (ext instanceof AttributeDef) {
            AttributeDef base = root((AttributeDef) ext);
            String baseDeclScoped = declaringClassScopedName(base);
            return isSuperclassOf(owner, baseDeclScoped);
        }
        return false;
    }
}
