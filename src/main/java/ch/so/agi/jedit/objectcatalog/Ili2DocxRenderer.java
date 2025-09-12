package ch.so.agi.jedit.objectcatalog;

import ch.interlis.ili2c.metamodel.*;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlBeans;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import java.util.*;
import java.math.BigInteger;

public final class Ili2DocxRenderer {
    private Ili2DocxRenderer() {}

    /** Renders models, topics and classes using real Heading styles. */
    public static void renderTransferDescription(XWPFDocument doc, TransferDescription td) {
        java.util.Objects.requireNonNull(td, "TransferDescription is null");
        ensureHeadingStyles(doc);

        Model[] last = td.getModelsFromLastFile();
        if (last == null) last = new Model[0];

        for (Model m : sortByName(last)) {
            // Heading 1: model name
            writeHeading(doc, m.getName(), 1);

            // Immediately after: paragraph with all model-level classes (outside topics)
            List<Table> rootClasses = getElements(m, Table.class);
            if (!rootClasses.isEmpty()) {
                XWPFParagraph p = doc.createParagraph(); // normal paragraph (no heading style)
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < rootClasses.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(rootClasses.get(i).getName());
                }
                p.createRun().setText(sb.toString());

                // For each model-level class: Heading 2 + table
                for (Table cls : rootClasses) {
                    writeClassHeading(doc, cls, 2);
                    writeAttributeTable(doc, collectRowsForClass(m, m, cls));
                }
            }

            // Each topic: Heading 1, then each class: Heading 2 + table
            for (Topic t : getElements(m, Topic.class)) {
                writeHeading(doc, t.getName(), 1); // topic as Heading 1
                for (Table cls : getElements(t, Table.class)) {
                    writeClassHeading(doc, cls, 2);
                    writeAttributeTable(doc, collectRowsForClass(m, t, cls));
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Headings & styles
    // ─────────────────────────────────────────────────────────────────────────────

    private static void ensureHeadingStyles(XWPFDocument doc) {
        XWPFStyles styles = doc.createStyles(); // ensure styles part exists

        if (!styles.styleExist("Title")) {
            styles.addStyle(buildHeadingStyle("Title", "title", /*outline*/ null, /*bold*/ true));
        }
        if (!styles.styleExist("Heading1")) {
            styles.addStyle(buildHeadingStyle("Heading1", "heading 1", java.math.BigInteger.ZERO, true));
        }
        if (!styles.styleExist("Heading2")) {
            styles.addStyle(buildHeadingStyle("Heading2", "heading 2", java.math.BigInteger.ONE, true));
        }
    }
    
    private static XWPFStyle buildHeadingStyle(String styleId, String name, java.math.BigInteger outlineLevel, boolean bold) {
        // Create CTStyle via XmlBeans loader (typed, schema-safe)
        CTStyle ctStyle = (CTStyle) XmlBeans.getContextTypeLoader().newInstance(CTStyle.type, null);
        ctStyle.setStyleId(styleId);
        ctStyle.setType(STStyleType.PARAGRAPH);
        ctStyle.addNewName().setVal(name);

        // Paragraph props (outline level if provided)
        CTPPr ppr = ctStyle.isSetPPr() ? ctStyle.getPPr() : ctStyle.addNewPPr();
        if (outlineLevel != null) {
            CTDecimalNumber lvl = ppr.isSetOutlineLvl() ? ppr.getOutlineLvl() : ppr.addNewOutlineLvl();
            lvl.setVal(outlineLevel); // 0=Heading1, 1=Heading2
        }

        // Run props: bold
        if (bold) {
            CTRPr rpr = ctStyle.isSetRPr() ? ctStyle.getRPr() : ctStyle.addNewRPr();
            if (!rpr.isSetB()) rpr.addNewB(); // <w:b/>
        }

        // Mark as quick/primary
        ctStyle.addNewQFormat();

        return new XWPFStyle(ctStyle);
    }
    
    private static int ensureHeadingNumbering(XWPFDocument doc) {
        XWPFNumbering numbering = doc.createNumbering(); // ensures numbering part exists

        // Reuse first existing num if present
        try {
            for (XWPFNum n : numbering.getNums()) {
                if (n != null && n.getCTNum() != null && n.getCTNum().getNumId() != null) {
                    return n.getCTNum().getNumId().intValue();
                }
            }
        } catch (Throwable ignore) {
            // some builds are finicky; we'll just create one
        }

        // Build abstract numbering: level 0 -> "1 ", level 1 -> "1.1 "
        CTAbstractNum ctAbs = (CTAbstractNum) XmlBeans.getContextTypeLoader()
                .newInstance(CTAbstractNum.type, null);
        ctAbs.setAbstractNumId(java.math.BigInteger.ONE);

        // lvl 0
        CTLvl lvl0 = ctAbs.addNewLvl();
        lvl0.setIlvl(java.math.BigInteger.ZERO);
        lvl0.addNewStart().setVal(java.math.BigInteger.ONE);
        lvl0.addNewNumFmt().setVal(STNumberFormat.DECIMAL);
        lvl0.addNewLvlText().setVal("%1 ");
        lvl0.addNewSuff().setVal(STLevelSuffix.SPACE);

        // lvl 1
        CTLvl lvl1 = ctAbs.addNewLvl();
        lvl1.setIlvl(java.math.BigInteger.ONE);
        lvl1.addNewStart().setVal(java.math.BigInteger.ONE);
        lvl1.addNewNumFmt().setVal(STNumberFormat.DECIMAL);
        lvl1.addNewLvlText().setVal("%1.%2 ");
        lvl1.addNewSuff().setVal(STLevelSuffix.SPACE);

        // Register abstract num
        XWPFAbstractNum xAbs = new XWPFAbstractNum(ctAbs);
        java.math.BigInteger absId = numbering.addAbstractNum(xAbs);
        if (absId == null) absId = java.math.BigInteger.ONE;

        // Bind a concrete num to it
        // Option A (preferred): simple add via absId
        java.math.BigInteger numId = null;
        try {
            numId = numbering.addNum(absId);
        } catch (Throwable ignore) {
            // Option B: construct CTNum explicitly
            CTNum ctNum = (CTNum) XmlBeans.getContextTypeLoader().newInstance(CTNum.type, null);
            ctNum.setNumId(java.math.BigInteger.ONE);
            ctNum.addNewAbstractNumId().setVal(absId);
            numId = numbering.addNum(new XWPFNum(ctNum, numbering));
        }
        if (numId == null) numId = java.math.BigInteger.ONE;

        return numId.intValue();
    }
    
    private static void applyNumbering(XWPFParagraph p, int level) {
        int numId = ensureHeadingNumbering(p.getDocument());
        CTPPr ppr = p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr();
        CTNumPr numPr = ppr.isSetNumPr() ? ppr.getNumPr() : ppr.addNewNumPr();

        (numPr.isSetNumId() ? numPr.getNumId() : numPr.addNewNumId())
                .setVal(java.math.BigInteger.valueOf(numId));
        (numPr.isSetIlvl() ? numPr.getIlvl() : numPr.addNewIlvl())
                .setVal(java.math.BigInteger.valueOf(level)); // 0 for H1, 1 for H2
    }

    
    private static void writeHeading(XWPFDocument doc, String text, int level) {
        XWPFParagraph p = doc.createParagraph();
        p.setStyle(level <= 1 ? "Heading1" : "Heading2");
        applyNumbering(p, level <= 1 ? 0 : 1);
        p.createRun().setText(text != null ? text : "");
    }

    
    private static void writeClassHeading(XWPFDocument doc, Table cls, int level) {
        XWPFParagraph p = doc.createParagraph();
        p.setStyle(level <= 1 ? "Heading1" : "Heading2");
        applyNumbering(p, level <= 1 ? 0 : 1);
        String stereos = classStereotypes(cls);
        String title = stereos.isEmpty() ? cls.getName() : (cls.getName() + " " + stereos);
        p.createRun().setText(title);
    }
    
    /** Returns "(Class)" | "(Abstract Class)" | "(Structure)". */
    private static String classStereotypes(Table t) {
        boolean structure = !t.isIdentifiable();
        boolean abs = t.isAbstract();
        if (structure) return "(Structure)";
        if (abs) return "(Abstract Class)";
        return "(Class)";
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Rows for a class (attributes + assoc roles)
    // ─────────────────────────────────────────────────────────────────────────────

    private static List<Row> collectRowsForClass(Model model, Container scope, Table cls) {
        List<Row> rows = new ArrayList<>();

        // attributes
        for (AttributeDef a : getElements(cls, AttributeDef.class)) {
            String type = typeName(a);
            if ("ObjectType".equalsIgnoreCase(type)) continue;
            rows.add(new Row(
                    a.getName(),
                    formatCardinality(a.getCardinality()),
                    type,
                    docOf(a)
            ));
        }

        // associations as attributes (role name, role cardinality, other endpoint type)
        for (AssociationDef as : collectAssociations(model, scope)) {
            List<RoleDef> roles = as.getRoles();
            if (roles == null || roles.size() != 2) continue;
            RoleDef left = roles.get(0);
            RoleDef right = roles.get(1);
            addAssocRowIfEndpoint(rows, left, right, cls);
            addAssocRowIfEndpoint(rows, right, left, cls);
        }

        return rows;
    }

    private static void addAssocRowIfEndpoint(List<Row> rows, RoleDef me, RoleDef other, Table thisClass) {
        AbstractClassDef dest = me.getDestination();
        AbstractClassDef otherDest = other.getDestination();
        if (dest instanceof Table && otherDest instanceof Table) {
            if (((Table) dest) == thisClass) {
                rows.add(new Row(
                        roleLabel(me),
                        formatCardinality(me.getCardinality()),
                        ((Table) otherDest).getName(),
                        ""  // association description: leave empty for now
                ));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Low-level CT* table creation (schema-safe)
    // ─────────────────────────────────────────────────────────────────────────────

    private static void writeAttributeTable(XWPFDocument doc, List<Row> rows) {
        CTBody body = doc.getDocument().getBody();
        CTTbl tbl = body.addNewTbl();

        final int COLS = 4;

        // simple grid (no widths; avoids CTTblWidth.setW)
        CTTblGrid grid = tbl.addNewTblGrid();
        for (int c = 0; c < COLS; c++) grid.addNewGridCol();

        // header
        CTRow hdr = tbl.addNewTr();
        addCellText(hdr, "Attributname");
        addCellText(hdr, "Kardinalität");
        addCellText(hdr, "Typ");
        addCellText(hdr, "Beschreibung");

        // data
        if (rows != null) {
            for (Row r : rows) {
                CTRow tr = tbl.addNewTr();
                addCellText(tr, nz(r.name));
                addCellText(tr, nz(r.card));
                addCellText(tr, nz(r.type));
                addCellText(tr, nz(r.descr));
            }
        }

        // spacing paragraph
        doc.createParagraph();
    }

    private static void addCellText(CTRow tr, String text) {
        CTTc tc = tr.addNewTc();
        CTP p = tc.addNewP();
        CTR run = p.addNewR();
        CTText t = run.addNewT();
        t.setStringValue(text != null ? text : "");
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // ili2c helpers
    // ─────────────────────────────────────────────────────────────────────────────

    private static final class Row {
        final String name, card, type, descr;
        Row(String name, String card, String type, String descr) {
            this.name = name; this.card = card; this.type = type; this.descr = descr;
        }
    }

    private static List<AssociationDef> collectAssociations(Model m, Container c) {
        List<AssociationDef> list = new ArrayList<>();
        for (AssociationDef as : getElements(c, AssociationDef.class)) list.add(as);
        return list;
    }

    private static String roleLabel(RoleDef r) {
        String n = r.getName();
        return (n != null && !n.isEmpty()) ? n : "role";
    }

    private static String typeName(AttributeDef a) {
        Type t = a.getDomain();
        if (t == null) return "<Unknown>";
        if (t instanceof ObjectType) return "ObjectType";
        if (t instanceof ReferenceType) {
            AbstractClassDef target = ((ReferenceType) t).getReferred();
            return target != null ? target.getName() : "Reference";
        }
        if (t instanceof CompositionType) {
            AbstractClassDef target = ((CompositionType) t).getComponentType();
            return target != null ? target.getName() : "Composition";
        }
        if (t instanceof EnumerationType) return a.isDomainBoolean() ? "Boolean" : a.getContainer().getName();
        if (t instanceof SurfaceType) return "Surface";
        if (t instanceof MultiSurfaceType) return "MultiSurface";
        if (t instanceof AreaType) return "Area";
        if (t instanceof MultiAreaType) return "MultiArea";
        if (t instanceof PolylineType) return "Polyline";
        if (t instanceof MultiPolylineType) return "MultiPolyline";
        if (t instanceof CoordType) {
            NumericalType[] nts = ((CoordType) t).getDimensions();
            return "Coord" + (nts != null ? nts.length : 0);
        }
        if (t instanceof MultiCoordType) {
            NumericalType[] nts = ((MultiCoordType) t).getDimensions();
            return "MultiCoord" + (nts != null ? nts.length : 0);
        }
        if (t instanceof NumericType) return "Numeric";
        if (t instanceof TextType) return "Text";
        if (t instanceof TextOIDType) {
            Type base = ((TextOIDType) t).getOIDType();
            if (base instanceof TypeAlias) return ((TypeAlias) base).getAliasing().getName();
            return base != null ? base.getName() : "TextOID";
        }
        if (t instanceof FormattedType && isDateOrTime((FormattedType) t)) {
            Domain base = ((FormattedType) t).getDefinedBaseDomain();
            return base != null ? base.getName() : "DateTime";
        }
        if (t instanceof TypeAlias) return ((TypeAlias) t).getAliasing().getName();

        String n = t.getName();
        return (n != null && !n.isEmpty()) ? n : t.getClass().getSimpleName();
    }

    private static boolean isDateOrTime(FormattedType ft) {
        Domain base = ft.getDefinedBaseDomain();
        return base == PredefinedModel.getInstance().XmlDate
            || base == PredefinedModel.getInstance().XmlDateTime
            || base == PredefinedModel.getInstance().XmlTime;
    }

    static String formatCardinality(Cardinality c) {
        if (c == null) return "1";
        long min = c.getMinimum();
        long max = c.getMaximum();
        String left = String.valueOf(min);
        String right = (max == Long.MAX_VALUE) ? "*" : String.valueOf(max);
        if (max >= 0 && min == max) return left;
        return left + ".." + right;
    }

    /** Pull description from ili2c: AttributeDef.getDocumentation() */
    private static String docOf(Element e) {
        if (e instanceof AttributeDef) {
            String s = ((AttributeDef) e).getDocumentation();
            return s != null ? s : "";
        }
        return "";
    }

    // sorted iteration utilities
    private static <T extends Element> List<T> getElements(Container c, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Iterator<?> it = c.iterator(); it.hasNext();) {
            Object e = it.next();
            if (type.isInstance(e)) out.add(type.cast(e));
        }
        out.sort(Comparator.comparing(Element::getName, Comparator.nullsLast(String::compareTo)));
        return out;
    }
    private static <T extends Element> List<T> sortByName(T[] arr) {
        if (arr == null) return Collections.emptyList();
        List<T> list = new ArrayList<>(Arrays.asList(arr));
        list.sort(Comparator.comparing(Element::getName, Comparator.nullsLast(String::compareTo)));
        return list;
    }
    private static String nz(String s) { return s == null ? "" : s; }
}
