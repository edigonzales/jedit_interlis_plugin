package ch.so.agi.jedit.uml;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ch.interlis.ili2c.metamodel.*;

import org.jhotdraw.draw.AttributeKey;
import org.jhotdraw.draw.AttributeKeys;
import org.jhotdraw.draw.Figure;
import org.jhotdraw.draw.GraphicalCompositeFigure;
import org.jhotdraw.draw.RectangleFigure;
import org.jhotdraw.draw.TextFigure;
import org.jhotdraw.draw.connector.ChopRectangleConnector;
import org.jhotdraw.draw.connector.Connector;

/**
 * JHotDraw 7.6 ClassFigure:
 * - Header with optional «View», «Structure» and/or «Abstract» lines + name
 * - Optional «extends ...» line (set via setForeignBaseLabel)
 * - Bottom border of header acts as separator
 * - Auto-sizes to content; origin preserved during layout
 */
public class ClassFigure extends GraphicalCompositeFigure {
    public static final AttributeKey<ClassFigure> OWNER_KEY =
            new AttributeKey<>("uml.class.owner", ClassFigure.class);

    /* ===== styling ===== */
    private static final double STROKE = 1.0;

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

    private TextFigure            viewTf      = null;   // optional «View»
    private TextFigure            structureTf = null;   // optional «Structure»
    private TextFigure            abstractTf  = null;   // optional «Abstract»
    private TextFigure            extendsTf   = null;   // optional «extends Topic::Base»

    private final List<TextFigure> rowFigs    = new ArrayList<>();

    /** Owner can be Table (class/structure) or View (and in general any Viewable). */
    private Viewable owner;

    /* ==================== constructors ==================== */

    /** New ctor: accepts any Viewable (Table/Class/Structure, View, …). */
    public ClassFigure(Viewable v) {
        this(v.getName(), collectRows(v));
        this.owner = v;

        // Stereotypes
        // - «View» if it’s a View
        if (v instanceof View) {
            viewTf = stereotype("«View»");
            add(viewTf);
        }

        // - «Structure» for non-identifiable Tables
        if (v instanceof Table) {
            Table t = (Table) v;
            if (!t.isIdentifiable()) {
                structureTf = stereotype("«Structure»");
                add(structureTf);
            }
        }

        // - «Abstract» when applicable (for AbstractClassDef)
        if (v instanceof AbstractClassDef) {
            if (((AbstractClassDef) v).isAbstract()) {
                abstractTf = stereotype("«Abstract»");
                add(abstractTf);
            }
        }
    }

    /** Kept: text + rows constructor, used by the Viewable ctor above. */
    public ClassFigure(String titleText, List<String> rows) {
        this.owner = null;

        // Make the composite the thing that connections and users interact with
        setConnectable(true);
        setSelectable(true);

        // Children not individually selectable/transformable/connectable
        outerRect.setSelectable(false);
        outerRect.setTransformable(false);
        outerRect.setConnectable(false);

        titleTf.setSelectable(false);
        titleTf.setTransformable(false);

        // presentation
        setPresentationFigure(outerRect);
        outerRect.set(AttributeKeys.STROKE_WIDTH, STROKE);
        outerRect.set(AttributeKeys.FILL_COLOR, Color.white);

        headerRect.set(AttributeKeys.STROKE_WIDTH, STROKE);
        headerRect.set(AttributeKeys.FILL_COLOR, null);
        add(headerRect);

        titleTf.setText(titleText);
        titleTf.set(AttributeKeys.FONT_BOLD, Boolean.FALSE);
        titleTf.set(AttributeKeys.FONT_SIZE, 12d);
        add(titleTf);

        for (String row : rows) {
            TextFigure tf = new TextFigure(row);
            tf.set(AttributeKeys.FONT_SIZE, 12d);
            tf.setSelectable(false);
            tf.setTransformable(false);
            rowFigs.add(tf);
            add(tf);
        }

        // tag everything so right-click/double-click on children finds the owner figure
        tagOwner(this);
        tagOwner(outerRect);
        tagOwner(headerRect);
        tagOwner(titleTf);
        for (TextFigure tf : rowFigs) tagOwner(tf);
    }

    private static TextFigure stereotype(String text) {
        TextFigure tf = new TextFigure(text);
        tf.set(AttributeKeys.FONT_BOLD, Boolean.FALSE);
        tf.set(AttributeKeys.FONT_SIZE, 12d);
        tf.setSelectable(false);
        tf.setTransformable(false);
        return tf;
    }

    /* ==================== API ==================== */

    public void setTitle(String txt) {
        titleTf.setText(txt);
        layout();
    }

    /** New primary accessor. */
    public Viewable getOwnerViewable() {
        return owner;
    }

    /** Back-compat shim: will return null for Views. */
    @Deprecated
    public Table getOwnerTable() {
        return (owner instanceof Table) ? (Table) owner : null;
    }

    public Connector connector() {
        return new ChopRectangleConnector(this);
    }

    @Override
    public Connector findConnector(java.awt.geom.Point2D.Double p, org.jhotdraw.draw.ConnectionFigure cf) {
        return new ChopRectangleConnector(this);
    }

    public void setBackgroundColor(Color c) {
        willChange();
        outerRect.set(AttributeKeys.FILL_COLOR, c);
        changed();
    }

    public Color getBackgroundColor() {
        return outerRect.get(AttributeKeys.FILL_COLOR);
    }

    /** Show/clear the cross-topic base indicator «extends …». */
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
            extendsTf = stereotype("«extends " + labelOrNull + "»");
            add(extendsTf);
        } else {
            extendsTf.setText("«extends " + labelOrNull + "»");
        }
        layout();
    }

    /** Adds a new (grey/italic) row under the separator and relayouts the figure. */
    public void addExternalRoleRow(String text) {
        TextFigure tf = new TextFigure(text);
        tf.set(AttributeKeys.FONT_SIZE, 12d);
        tf.set(AttributeKeys.TEXT_COLOR, new java.awt.Color(90, 90, 90));
        tf.set(AttributeKeys.FONT_ITALIC, Boolean.TRUE);
        tf.setSelectable(false);
        tf.setTransformable(false);

        rowFigs.add(tf);
        add(tf);
        layout();
    }

    /* ==================== layout ==================== */

    @Override
    public void layout() {
        Rectangle2D old = outerRect.getBounds();
        double ox = old.getX();
        double oy = old.getY();

        // measure title
        Rectangle2D tb = titleTf.getBounds();
        double tW = Math.max(1, tb.getWidth());
        double tH = Math.max(1, tb.getHeight());

        // measure stereotypes
        double vW = 0, vH = 0;
        if (viewTf != null) {
            Rectangle2D vb = viewTf.getBounds();
            vW = Math.max(1, vb.getWidth());
            vH = Math.max(1, vb.getHeight());
        }
        double sW = 0, sH = 0;
        if (structureTf != null) {
            Rectangle2D sb = structureTf.getBounds();
            sW = Math.max(1, sb.getWidth());
            sH = Math.max(1, sb.getHeight());
        }
        double aW = 0, aH = 0;
        if (abstractTf != null) {
            Rectangle2D ab = abstractTf.getBounds();
            aW = Math.max(1, ab.getWidth());
            aH = Math.max(1, ab.getHeight());
        }
        double eW = 0, eH = 0;
        if (extendsTf != null) {
            Rectangle2D eb = extendsTf.getBounds();
            eW = Math.max(1, eb.getWidth());
            eH = Math.max(1, eb.getHeight());
        }

        double headerInnerW = HPAD_L + max(tW, vW, sW, aW, eW) + HPAD_R;

        // measure rows
        double maxRowW = 0;
        double rowsH = 0;
        for (int i = 0; i < rowFigs.size(); i++) {
            Rectangle2D rb = rowFigs.get(i).getBounds();
            double rW = Math.max(1, rb.getWidth());
            double rH = Math.max(1, rb.getHeight());
            maxRowW = Math.max(maxRowW, rW);
            rowsH += rH;
            if (i < rowFigs.size() - 1) rowsH += ROW_GAP;
        }

        double rowsInnerW = CPAD_L + maxRowW + CPAD_R;
        double innerW = Math.max(headerInnerW, rowsInnerW);
        double totalW = PAD_L + innerW + PAD_R;

        double y = PAD_T;

        // header height = padding + (stereotypes with gaps) + title + padding
        double headerH = HPAD_T
                + (viewTf      != null ? vH + ABSTRACT_GAP : 0)
                + (structureTf != null ? sH + ABSTRACT_GAP : 0)
                + (abstractTf  != null ? aH + ABSTRACT_GAP : 0)
                + (extendsTf   != null ? eH + ABSTRACT_GAP : 0)
                + tH
                + HPAD_B;

        // header rect
        headerRect.setBounds(
                new Point2D.Double(ox + PAD_L, oy + y),
                new Point2D.Double(ox + PAD_L + innerW, oy + y + headerH)
        );

        // place header text lines
        double textY = oy + y + HPAD_T;
        double tx = ox + PAD_L + HPAD_L;

        if (viewTf != null) {
            setBounds(viewTf, tx, textY, vW, vH);
            textY += vH + ABSTRACT_GAP;
        }
        if (structureTf != null) {
            setBounds(structureTf, tx, textY, sW, sH);
            textY += sH + ABSTRACT_GAP;
        }
        if (abstractTf != null) {
            setBounds(abstractTf, tx, textY, aW, aH);
            textY += aH + ABSTRACT_GAP;
        }
        if (extendsTf != null) {
            setBounds(extendsTf, tx, textY, eW, eH);
            textY += eH + ABSTRACT_GAP;
        }

        setBounds(titleTf, tx, textY, tW, tH);
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

    private static void setBounds(TextFigure tf, double x, double y, double w, double h) {
        tf.setBounds(new Point2D.Double(x, y), new Point2D.Double(x + w, y + h));
    }

    private static double max(double... v) {
        double m = 0;
        for (double d : v) if (d > m) m = d;
        return m;
    }

    private void tagOwner(Figure f) { f.set(OWNER_KEY, this); }

    /* ==================== helpers (rows) ==================== */

    private static List<String> collectRows(Viewable v) {
        ArrayList<String> rows = new ArrayList<>();
        for (Iterator<?> it = v.getAttributesAndRoles2(); it.hasNext();) {
            ViewableTransferElement vte = (ViewableTransferElement) it.next();
            Object o = vte.obj;
            if (o instanceof AttributeDef) {
                AttributeDef a = (AttributeDef) o;

                // Views enthalten Mutterobjekt.
                // Ich weiss nicht, ob ich nun andere Dinge ausschliesse.
                if (a.getDomain() instanceof ObjectType) {
                    continue;
                } 
                
                AttrKind kind = classify(v, a);
                String label  = formatAttribute(a);

                switch (kind) {
                    case INHERITED:
                        // keep hidden / or annotate if you like
                        // rows.add(label + "  «from " + shortName(declaringClassScopedName(root(a))) + "»");
                        break;
                    case OVERRIDES:
                        // AttributeDef base = (AttributeDef) a.getExtending();
                        // rows.add(label + "  «overrides …»");
                        rows.add(label);
                        break;
                    default:
                        rows.add(label);
                }
            }
        }
        return rows;
    }

    private static String formatAttribute(AttributeDef a) {
        String typeName = "?";
        Type t = a.getDomain();
        if (t != null) {
            if (t instanceof ReferenceType) {
                ReferenceType ref = (ReferenceType) t;
                AbstractClassDef target = ref.getReferred();
                if (target != null) return a.getName() + card(a) + " : " + target.getName();
            } else if (t instanceof CompositionType) {
                AbstractClassDef target = ((CompositionType) t).getComponentType();
                if (target != null) typeName = target.getName();
            } else if (t instanceof TextType) {
                typeName = "String";
            } else if (t instanceof NumericType) {
                typeName = "Numeric";
            } else if (t instanceof FormattedType && isDateOrTime((FormattedType) t)) {
                typeName = ((FormattedType) t).getDefinedBaseDomain().getName();
            } else if (t instanceof MultiAreaType) {
                typeName = "MultiArea";
            } else if (t instanceof AreaType) {
                typeName = "Area";
            } else if (t instanceof MultiSurfaceType) {
                typeName = "MultiSurface";
            } else if (t instanceof SurfaceType) {
                typeName = "Surface";
            } else if (t instanceof PolylineType) {
                typeName = "Polyline";
            } else if (t instanceof MultiPolylineType) {
                typeName = "MultiPolyline";
            } else if (t instanceof CoordType) {
                NumericalType[] nts = ((CoordType) t).getDimensions();
                typeName = "Coord" + nts.length;
            } else if (t instanceof MultiCoordType) {
                NumericalType[] nts = ((MultiCoordType) t).getDimensions();
                typeName = "MultiCoord" + nts.length;
            } else if (t instanceof EnumerationType) {
                typeName = a.isDomainBoolean() ? "Boolean" : a.getContainer().getName();
            } else if (t instanceof TextOIDType) {
                Type textOidType = ((TextOIDType) t).getOIDType();
                if (textOidType instanceof TypeAlias) typeName = ((TypeAlias) textOidType).getAliasing().getName();
                else typeName = textOidType.getName();
            } else if (t instanceof TypeAlias) {
                typeName = ((TypeAlias) t).getAliasing().getName();
            }
        }
        return a.getName() + card(a) + " : " + typeName;
    }

    private static String card(AttributeDef a) {
        Cardinality c = a.getCardinality();
        if (c == null) return "";
        return c.toString().replace('{','[').replace('}',']');
    }

    private static boolean isDateOrTime(FormattedType ft) {
        Domain base = ft.getDefinedBaseDomain();
        return base == PredefinedModel.getInstance().XmlDate
            || base == PredefinedModel.getInstance().XmlDateTime
            || base == PredefinedModel.getInstance().XmlTime;
    }

    /* ==================== inheritance helpers ==================== */

    enum AttrKind { DECLARED_HERE, INHERITED, OVERRIDES }

    /** Scoped name (e.g., Model.Topic.Class) of the class that declared this attribute, or null. */
    private static String declaringClassScopedName(AttributeDef a) {
        Container<?> c = a.getContainer(Viewable.class);
        return (c != null) ? c.getScopedName(null) : null;
    }

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

    /** Classify an attribute relative to the owning viewable. */
    private static AttrKind classify(Viewable owner, AttributeDef attr) {
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
