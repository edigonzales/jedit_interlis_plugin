package ch.so.agi.jedit.compile;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.gjt.sp.jedit.Buffer;

import ch.interlis.ili2c.metamodel.Container;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.Viewable;

public class AstIndex {

    // startOffset → element
    private final NavigableMap<Integer,Element> byStart = new TreeMap<>();
    /** cached line start offsets of the buffer (0-based) */
    private final int[] lineStart;
    
    public AstIndex(Buffer buffer, TransferDescription td) {
        this.lineStart = buildLineIndex(buffer);

        // Only index elements that belong to the current buffer/file
        Model[] models = td.getModelsFromLastFile();
        if (models == null) return;

        for (Model m : models) {
            indexElement(m);
            // Walk everything inside the model
            walkContainer(m);
        }
    }
    
    /* ---------- public lookups ---------- */

    /** Element whose declaration starts at or before caret. */
    public Element atOrAbove(int caretOffset) {
        var e = byStart.floorEntry(caretOffset);
        return (e != null) ? e.getValue() : null;
    }

    /** Start offset of the element that contains or precedes caret. */
    public int startOffsetAtOrAbove(int caretOffset) {
        var e = byStart.floorEntry(caretOffset);
        return (e != null) ? e.getKey() : -1;
    }
    
    public java.util.Map.Entry<Integer, ch.interlis.ili2c.metamodel.Element>
    floor(int caret) {
        return byStart.floorEntry(caret);
    }

    public java.util.Map.Entry<Integer, ch.interlis.ili2c.metamodel.Element>
    lower(int startKey) {
        return byStart.lowerEntry(startKey);
    }
    
    /* ---------- internal indexing ---------- */

    private void walkContainer(Container c) {
        for (Iterator<?> it = c.iterator(); it.hasNext();) {
            Element e = (Element) it.next();

            // We care about Topics and Viewables (CLASS/STRUCTURE/VIEW)
            if (e instanceof Topic) {
                indexElement(e);
                walkContainer((Topic) e); // Topic is a Container
            } else if (e instanceof Viewable) {
                indexElement(e);
                // Viewables can also be Containers (e.g. nested), so descend
                if (e instanceof Container) walkContainer((Container) e);
            } else if (e instanceof Container) {
                // Other containers we might want to descend into (packages, etc.)
                walkContainer((Container) e);
            }
        }
    }

    private void indexElement(Element e) {
        int line = safeLine(e);                 // 1-based; <=0 means unknown
        if (line <= 0 || line > lineStart.length) return;

        int start = lineStart[line - 1];        // start of that line
        byStart.put(start, e);
    }
    
    /* ---------- utilities ---------- */
    
    /** ili2c returns 1-based lines; 0 or negative means “unknown”. */
    private static int safeLine(Element e) {
        try { return e.getSourceLine(); }
        catch (Throwable t) { return -1; }
    }
    
    private static int[] buildLineIndex(Buffer b) {
        int n = b.getLineCount();
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) idx[i] = b.getLineStartOffset(i);
        return idx;
    }
}
