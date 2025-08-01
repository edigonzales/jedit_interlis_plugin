package ch.so.agi.jedit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.EditPane;
import org.gjt.sp.jedit.View;

import errorlist.DefaultErrorSource;
import sidekick.SideKickCompletion;
import sidekick.SideKickParsedData;
import sidekick.SideKickParser;

public class InterlisSideKickParser extends SideKickParser {
    
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
    
    /* ==================================================================
     * 1) OUTLINE (not implemented yet)
     * ================================================================== */
    public SideKickParsedData parse(Buffer buffer, DefaultErrorSource es) {
        // We don’t build an outline yet – just return an empty tree
        return new SideKickParsedData(buffer.getName());
    }
    
    /* ==================================================================
     * 2) COMPLETION – triggered by Ctrl+Space or SideKick "Complete"
     * ================================================================== */
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
