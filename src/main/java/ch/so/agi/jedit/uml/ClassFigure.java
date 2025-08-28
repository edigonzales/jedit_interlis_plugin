package ch.so.agi.jedit.uml;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
        for (java.util.Iterator<?> it = clazz.getAttributesAndRoles2(); it.hasNext();) {
            ViewableTransferElement vte = (ViewableTransferElement) it.next();
            Object o = vte.obj;
            if (o instanceof AttributeDef) {
                AttributeDef a = (AttributeDef) o;

                AttrKind kind = classify(clazz, a);
                String label  = formatAttribute(a);

                switch (kind) {
                    // TODO: default nicht anzeigen, aber Option?
                    case INHERITED: {
                        // where was it originally declared?
                        String baseDeclScoped = declaringClassScopedName(root(a)); // top-most origin
                        rows.add(label + "  «from " + shortName(baseDeclScoped) + "»");
                        break;
                    }
                    // TODO: immer anzeigen?
                    case OVERRIDES: {
                        // which base does it extend/override?
                        AttributeDef base = (AttributeDef) a.getExtending();
                        String baseDeclScoped = declaringClassScopedName(root(base));
                        rows.add(label + "  «overrides " + shortName(baseDeclScoped) + "." + base.getName() + "»");
                        break;
                    }
                    default: // DECLARED_HERE
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
    
    /* ===== Inheritance helpers for attributes ===== */

    /* ----------------------- helpers ----------------------- */
    enum AttrKind { DECLARED_HERE, INHERITED, OVERRIDES }

    /** Scoped name (e.g., Model.Topic.Class) of the class that declared this attribute, or null. */
    private static String declaringClassScopedName(AttributeDef a) {
        Container<?> c = a.getContainer(Viewable.class); // nearest container of type Viewable
        return (c != null) ? c.getScopedName(null) : null;
    }

    /** Simple class name from a scoped name. */
    private static String shortName(String scoped) {
        if (scoped == null) return "?";
        int i = scoped.lastIndexOf('.');
        return (i >= 0) ? scoped.substring(i + 1) : scoped;
    }

    /** True if `otherScoped` is a superclass (direct/indirect) of `owner`. */
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

    /** Top-most ancestor attribute in an extension chain (or the attribute itself). */
    private static AttributeDef root(AttributeDef a) {
        AttributeDef r = a.getRootExtending();
        return (r != null) ? r : a;
    }

    /** Classify an attribute relative to the owning class. */
    private static AttrKind classify(Table owner, AttributeDef attr) {
        String ownerScoped = owner.getScopedName(null);
        String declScoped  = declaringClassScopedName(attr);

        if (!ownerScoped.equals(declScoped)) {
            // Appears on owner but declared elsewhere → inherited if that elsewhere is a superclass
            return isSuperclassOf(owner, declScoped) ? AttrKind.INHERITED : AttrKind.DECLARED_HERE;
        }
        // Declared here; check if it overrides/extends a base attribute
        Element ext = attr.getExtending();
        if (ext instanceof AttributeDef) {
            AttributeDef base = (AttributeDef) ext;
            String baseDeclScoped = declaringClassScopedName(base);
            if (isSuperclassOf(owner, baseDeclScoped)) return AttrKind.OVERRIDES;
        }
        return AttrKind.DECLARED_HERE;
    }
    
    /* ------------------ main predicate --------------------- */

    /** Returns true if `attr` comes from a superclass of `owner` (inherited or overrides). */
    private static boolean isDerivedFromSuperclass(Viewable owner, AttributeDef attr) {
        String declaredHereScoped = declaringClassScopedName(attr);
        String ownerScoped        = owner.getScopedName(null);

        if (!ownerScoped.equals(declaredHereScoped)) {
            // Declared somewhere else -> inherited if that "somewhere" is a superclass
            return isSuperclassOf(owner, declaredHereScoped);
        }

        // Declared in this class; it may still be an override/extension of a base attribute
        Element ext = attr.getExtending();
        if (ext instanceof AttributeDef) {
            AttributeDef base = root((AttributeDef) ext);
            String baseDeclScoped = declaringClassScopedName(base);
            return isSuperclassOf(owner, baseDeclScoped);
        }
        return false;
    }}
