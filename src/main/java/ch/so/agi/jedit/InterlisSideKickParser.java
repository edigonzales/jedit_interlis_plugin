package ch.so.agi.jedit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.Icon;
import javax.swing.text.Position;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.EditPane;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;

import errorlist.DefaultErrorSource;
import sidekick.Asset;
import sidekick.SideKickCompletion;
import sidekick.SideKickParsedData;
import sidekick.SideKickParser;
import sidekick.util.SideKickAsset;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.ehi.basics.logging.EhiLogger;
import ch.interlis.ili2c.Ili2cSettings;
import ch.interlis.ili2c.config.Configuration;
import ch.interlis.ili2c.config.FileEntry;
import ch.interlis.ili2c.config.FileEntryKind;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.Ili2cMetaAttrs;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.Topic;

public class InterlisSideKickParser extends SideKickParser {
    private static final String P_REPOS     = "interlis.repos";
    
    private static final List<String> KEYWORDS = List.of(
            "ABSTRACT", "ACCORDING", "AGGREGATES", "AGGREGATION", "ALL", "AND", "ANY", "ANYCLASS", "ANYSTRUCTURE",
            "ARCS", "AREA", "AS", "ASSOCIATION", "AT", "ATTRIBUTE", "ATTRIBUTES", "BAG", "BASE", "BASED", "BASKET",
            "BINARY", "BLACKBOX", "BOOLEAN", "BY", "CARDINALITY", "CHARSET", "CIRCULAR", "CLASS", "CLOCKWISE",
            "CONSTRAINT", "CONSTRAINTS", "CONTEXT", "CONTINUE", "CONTINUOUS", "CONTRACTED", "COORD", "COUNTERCLOCKWISE",
            "DATE", "DATETIME", "DEFERRED", "DEFINED", "DEPENDS", "DERIVED", "DIRECTED", "DOMAIN", "END", "ENUMTREEVAL",
            "ENUMVAL", "EQUAL", "EXISTENCE", "EXTENDED", "EXTENDS", "EXTERNAL", "FINAL", "FIRST", "FORM", "FROM",
            "FUNCTION", "GENERIC", "GENERICS", "GRAPHIC", "HALIGNMENT", "HIDING", "IMPORTS", "IN", "INHERITANCE",
            "INSPECTION", "INTERLIS", "JOIN", "LAST", "LINE", "LIST", "LNBASE", "LOCAL", "MANDATORY", "METAOBJECT",
            "MULTIAREA", "MULTICOORD", "MULTIPOLYLINE", "MULTISURFACE", "MODEL", "MTEXT", "NAME", "NOT", "NO",
            "NOINCREMENTALTRANSFER", "NULL", "NUMERIC", "OBJECT", "OF", "OID", "ON", "OR", "ORDERED", "OTHERS",
            "OVERLAPS", "PARAMETER", "PARENT", "PI", "POLYLINE", "PROJECTION", "REFERENCE", "REFSYS", "REFSYSTEM",
            "REQUIRED", "RESTRICTED", "ROTATION", "SET", "SIGN", "STRAIGHTS", "STRUCTURE", "SUBDIVISION", "SURFACE",
            "SYMBOLOGY", "TEXT", "THATAREA", "THIS", "THISAREA", "TIMEOFDAY", "TO", "TOPIC", "TRANSIENT", "TRANSLATION",
            "TYPE", "UNDEFINED", "UNION", "UNIQUE", "UNIT", "UNQUALIFIED", "URI", "VALIGNMENT", "VERSION", "VERTEX",
            "VIEW", "WHEN", "WHERE", "WITH", "WITHOUT", "XMLNS"
        );
    
    public InterlisSideKickParser() {
        super("interlis_parser");
    }
    
    // Outline
    public SideKickParsedData parse(Buffer buffer, DefaultErrorSource es) {
        SideKickParsedData data = new SideKickParsedData(buffer.getName());
        DefaultMutableTreeNode root = data.root;
        
        Ili2cMetaAttrs ili2cMetaAttrs = new Ili2cMetaAttrs();
        
        String ilidirs = jEdit.getProperty(P_REPOS);
        if (ilidirs == null || ilidirs.isEmpty()) {
            ilidirs = Ili2cSettings.DEFAULT_ILIDIRS;
        }
       
        Ili2cSettings settings = new Ili2cSettings();
        ch.interlis.ili2c.Main.setDefaultIli2cPathMap(settings);
        settings.setIlidirs(ilidirs);

        Configuration config = new Configuration();
        FileEntry file = new FileEntry(buffer.getPath(), FileEntryKind.ILIMODELFILE);
        config.addFileEntry(file);
        config.setAutoCompleteModelList(true);  
        config.setGenerateWarnings(true);

        TransferDescription td = ch.interlis.ili2c.Main.runCompiler(config, settings, ili2cMetaAttrs);
        if (td == null) return data;

        for (Iterator<?> it = td.iterator(); it.hasNext();) {
            Object obj = it.next();
            if (!(obj instanceof Model)) continue;
            Model m = (Model) obj;

            DefaultMutableTreeNode mNode = node(buffer, m, m.getName());
            root.add(mNode);

//            for (Topic t : m) {
//                DefaultMutableTreeNode tNode = node(buffer, t, t.getName());
//                mNode.add(tNode);
//
//                for (ClassDef c : t.getClassDefs()) {
//                    tNode.add(node(buffer, c, c.getName()));
//                }
//            }
        }
        
        return data;    
    }
    
    private DefaultMutableTreeNode node(Buffer buf, Element e, String label) {
        int line   = Math.max(e.getSourceLine() - 1, 0); // ili2c is 1-based
        int offset = buf.getLineStartOffset(line);

        SimpleAsset asset = new SimpleAsset(label);
        asset.setStart(buf.createPosition(offset));

        return new DefaultMutableTreeNode(asset);
    }
    
    private static final class SimpleAsset extends Asset {
        SimpleAsset(String name) { super(name); }

        @Override public javax.swing.Icon getIcon() { return null; }
        @Override public String getShortString() { return getName(); }
        @Override public String getLongString() { return getName(); }
    }
    
    // Completion
    @Override
    public SideKickCompletion complete(EditPane editPane, int caret) {

        Buffer buf = editPane.getBuffer();

        /* Find the current word fragment directly before the caret */
        int start = caret - 1;
        while (start >= 0 && Character.isLetter(buf.getText(start, 1).charAt(0)))
            start--;
        start++;
        if (start >= caret) return null;

        String prefix = buf.getText(start, caret - start);

        /* Build a case-insensitive match list */
        List<String> matches = new ArrayList<>();
        for (String kw : KEYWORDS)
            if (kw.startsWith(prefix.toUpperCase())    // same prefix …
                && !kw.equalsIgnoreCase(prefix))       // … but not identical
                matches.add(kw);

        if (matches.isEmpty())
            return null;

        Collections.sort(matches);
        return new KeywordCompletion(editPane.getView(), buf, start, caret, matches);
    }
    
    // Small helper class that replaces the prefix with the chosen keyword
    private static class KeywordCompletion extends SideKickCompletion {
        private final Buffer buffer;
        private final int start, end;
        private final List<String> items; // keep a copy for later

        KeywordCompletion(View view, Buffer buf, int s, int e, List<String> items) {
            super(view, "", items); // "" because prefix already exists
            this.buffer = buf;
            this.start  = s;
            this.end    = e;
            this.items  = items;
        }   
        
        //
        @Override   
        public void insert(int index) {
            if (index < 0 || index >= items.size())
                return;

            String replacement = items.get(index);
            buffer.remove(start, end - start);
            buffer.insert(start, replacement);
        }
    }
}
