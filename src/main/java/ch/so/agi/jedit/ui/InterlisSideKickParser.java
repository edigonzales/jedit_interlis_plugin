package ch.so.agi.jedit.ui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.text.Position;
import javax.swing.tree.DefaultMutableTreeNode;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.EditPane;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.Log;

import errorlist.DefaultErrorSource;
import sidekick.Asset;
import sidekick.SideKickCompletion;
import sidekick.SideKickParsedData;
import sidekick.SideKickParser;
import sidekick.SideKickPlugin;

import ch.interlis.ili2c.metamodel.AssociationDef;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.Container;
import ch.interlis.ili2c.metamodel.Domain;
import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.Topic;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.Viewable;
import ch.interlis.ili2c.metamodel.ViewableTransferElement;

import ch.so.agi.jedit.compile.TdCache;

public class InterlisSideKickParser extends SideKickParser {

    public InterlisSideKickParser() {
        super("interlis_parser");
    }

    /* =============================== Outline =============================== */

    // Wird ausgelöst:
    // - beim Starten von jEdit, wenn eine ili-Datei geöffnet ist.
    // - beim Öffnen von Sidekick (also des Trees ("Outline"))
    @Override
    public SideKickParsedData parse(Buffer buffer, DefaultErrorSource es) {
//        // Always return quickly with whatever we have in the cache.
//        final SideKickParsedData data = new SideKickParsedData(buffer.getName());
//        final DefaultMutableTreeNode root = data.root;
//
//        System.err.println("****** parse()");
//        
//        TransferDescription td = TdCache.peek(buffer); // non-blocking, maybe null
//        if (td != null) {
//            buildTree(buffer, td, root);
//            return data;
//        }
//
//        // If TD isn’t available (e.g., unsaved changes), do not trigger a compile here.
//        // When your CompileService pushes a new TD to TdCache, SideKick can be told to re-parse:
//        //   SideKickPlugin.parse(jEdit.getActiveView(), true);
//        return data;
        
        // 0. Create an (initially empty) tree. 
        final SideKickParsedData data = new SideKickParsedData(buffer.getName());
        final DefaultMutableTreeNode root = data.root;
        
        System.err.println("**** SideKickParsedData parse");
        
        // 1. Try to get a ready TransferDescription from cache 
        // td ist null, falls der Buffer nicht im Cache ist oder
        // nicht mehr gültig, weil sich die Datei geändert hat (auf
        // dem Filesystem).
        TransferDescription td = TdCache.peek(buffer);
        Log.log(Log.DEBUG, this, "TransferDescription is stale or missing.");
        System.err.println("ist td null (= stale or missing)? ");

        if (td != null) { // cached & up-to-date
            System.err.println("td ist nicht null: " + td.getLastModel());
            System.err.println("outline / tree wird erstellt. Anschliessend return");
            buildTree(buffer, td, root);
            return data; // full outline
        }
        
        // 2. Schedule compile in background if not cached (or stale).  
        System.err.println("td null (entweder nicht im Cache oder Datei hat sich geändert) ist, es wird der Cache erstellt.");
        TdCache.get(buffer).thenAccept(ast -> {
            if (ast == null) return; // syntax error

            System.err.println("**** thenAccept");

            // Re-parse the buffer on the EDT so SideKick can rebuild UI 
            SwingUtilities.invokeLater(() -> 
                // Diese parse-Methode
                SideKickPlugin.parse(jEdit.getActiveView(), true));
            });

        // 3. Return empty tree right away – it will be replaced later. 
        return data;
    }

    private void buildTree(Buffer buffer, TransferDescription td, DefaultMutableTreeNode root) {
        for (Model model : td.getModelsFromLastFile()) {
            DefaultMutableTreeNode mNode = node(buffer, model, "MODEL " + model.getName());
            root.add(mNode);

            // If you prefer not to show imported models in the outline, remove this block.
            Model[] importedModels = model.getImporting();
            if (importedModels != null) {
                for (Model importedModel : importedModels) {
                    if (importedModel == null) continue;
                    DefaultMutableTreeNode imNode = node(buffer, importedModel, "MODEL " + importedModel.getName());
                    mNode.add(imNode);
                }
            }

            processContainer(buffer, model, mNode);
        }
    }

    private void processContainer(Buffer buffer, Container container, DefaultMutableTreeNode parentNode) {
        for (Iterator<?> it = container.iterator(); it.hasNext();) {
            Element element = (Element) it.next();

            if (element instanceof Topic) {
                Topic topic = (Topic) element;
                DefaultMutableTreeNode tNode = node(buffer, topic, "TOPIC " + topic.getName());
                parentNode.add(tNode);
                processContainer(buffer, topic, tNode);
            }
            else if (element instanceof Viewable) {
                Viewable v = (Viewable) element;
                String kind = getViewableType(v);
                String label = kind + " " + v.getName();
                if (v.isAbstract()) label += " (ABSTRACT)";
                DefaultMutableTreeNode vNode = node(buffer, v, label);
                parentNode.add(vNode);
                processAttributes(buffer, v, vNode);
            }
            else if (element instanceof Domain) {
                Domain d = (Domain) element;
                DefaultMutableTreeNode dNode = node(buffer, d, "DOMAIN " + d.getName());
                parentNode.add(dNode);
            }
        }
    }

    private void processAttributes(Buffer buffer, Viewable viewable, DefaultMutableTreeNode parentNode) {
        for (Iterator<?> it = viewable.getAttributesAndRoles2(); it.hasNext();) {
            ViewableTransferElement vte = (ViewableTransferElement) it.next();
            if (vte.obj instanceof AttributeDef) {
                AttributeDef a = (AttributeDef) vte.obj;
                DefaultMutableTreeNode aNode = node(buffer, a, a.getName());
                parentNode.add(aNode);
            }
        }
    }

    private String getViewableType(Viewable v) {
        if (v instanceof ch.interlis.ili2c.metamodel.Table) {
            ch.interlis.ili2c.metamodel.Table t = (ch.interlis.ili2c.metamodel.Table) v;
            return t.isIdentifiable() ? "CLASS" : "STRUCTURE";
        }
        if (v instanceof AssociationDef) return "ASSOCIATION";
        if (v instanceof ch.interlis.ili2c.metamodel.View) return "VIEW";
        return "Viewable";
        }

    private DefaultMutableTreeNode node(Buffer buffer, Element e, String label) {
        int line = Math.max(e.getSourceLine() - 1, 0); // ili2c lines are 1-based
        int lineStart = buffer.getLineStartOffset(line);
        int lineEnd   = buffer.getLineEndOffset(line);
        SimpleAsset asset = new SimpleAsset(label);
        Position startPos = buffer.createPosition(lineStart);
        Position endPos   = buffer.createPosition(lineEnd);
        asset.setStart(startPos);
        asset.setEnd(endPos);
        return new DefaultMutableTreeNode(asset);
    }

    private static final class SimpleAsset extends Asset {
        SimpleAsset(String name) { super(name); }
        @Override public javax.swing.Icon getIcon() { return null; }
        @Override public String getShortString() { return getName(); }
        @Override public String getLongString() { return getName(); }
    }

    /* ============================= Completion ============================== */

    @Override
    public SideKickCompletion complete(EditPane editPane, int caret) {
        System.err.println("*********** complete()");
        Buffer buf = editPane.getBuffer();

        System.err.println("*********** complete() buffer is dirty: " + buf.isDirty());
        
        TransferDescription td = TdCache.peekLastValid(buf);
        if (td == null) {
            Log.log(Log.DEBUG, this, "No valid run yet for: " + buf.getName());
            return null;
        }
        
        System.err.println("*********** complete() 2");
        
        int line = editPane.getTextArea().getCaretLine();
        int lineStart = buf.getLineStartOffset(line);
        int uptoCaret = Math.max(0, caret - lineStart);
        String lineText = buf.getText(lineStart, uptoCaret);
        
        System.err.println("*********** complete() 3");

        // ---- DOT CONTEXT: ModelName.<prefix> ----
        int lastDot = lineText.lastIndexOf('.');
        if (lastDot >= 0) {
            String before = lineText.substring(0, lastDot);
            String after  = lineText.substring(lastDot + 1); // member prefix
            //if (after.length() == 0) return null;            // need at least 1 char

            
            System.err.println("*********** complete() 4");

            
            String typedModel = lastIdentifier(before);
            if (typedModel != null && !typedModel.isEmpty()) {
                
                System.err.println("*********** complete() 5");

                String canonical = findCanonicalModelName(td, typedModel);
                System.err.println("*********** complete() 6 (canonical model name)" + canonical);
                // Wahrscheinlich liegt hier der Hund begraben, weil wir den Buffer für was verwenden, der geändert hat.
                List<String> members = TdCache.getMembersOfModel(buf, canonical); // top-level members of that model
                System.err.println("members: " + members);
                if (!members.isEmpty()) {
                    List<String> matches = startsWithFilterCI(members, after);
                    if (!matches.isEmpty()) {
                        int start = caret - after.length();
                        return new KeywordCompletion(editPane.getView(), buf, start, caret, matches);
                    }
                }
            }
            return null; // dot context but we can't resolve → no generic list
        }

        System.err.println("*********** complete() 10");

        
        // ---- PLAIN WORD: model names (local + direct imports) only ----
        String word = currentWord(lineText);
        if (word.length() == 0) return null; // must type at least one character

        List<String> modelNames = modelNamesForBuffer(td);
        System.err.println("modelNames: " + modelNames);
        List<String> matches = startsWithFilterCI(modelNames, word);
        if (matches.isEmpty()) return null;

        // de-dup case-insensitively, keep display case
        LinkedHashMap<String,String> uniq = new LinkedHashMap<>();
        for (String s : matches) {
            if (s == null) continue;
            String key = s.toUpperCase(java.util.Locale.ROOT);
            uniq.putIfAbsent(key, s);
        }
        ArrayList<String> finalList = new ArrayList<>(uniq.values());
        finalList.sort(String.CASE_INSENSITIVE_ORDER);

        int start = caret - word.length();
        return new KeywordCompletion(editPane.getView(), buf, start, caret, finalList);
    }

    /* ============================== helpers =============================== */

    // trailing word on the line (letters/digits/underscore)
    private static String currentWord(String s) {
        int i = s.length() - 1;
        while (i >= 0) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') i--; else break;
        }
        return s.substring(i + 1);
    }

    // last identifier at end of string
    private static String lastIdentifier(String s) {
        int i = s.length() - 1;
        while (i >= 0) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') i--; else break;
        }
        int start = i + 1;
        return (start < s.length()) ? s.substring(start) : null;
    }

    // case-insensitive startsWith, returns original-cased items
    private static List<String> startsWithFilterCI(List<String> items, String prefix) {
        if (items == null || items.isEmpty()) return java.util.Collections.emptyList();
        final String p = (prefix == null) ? "" : prefix.toUpperCase(java.util.Locale.ROOT);
        ArrayList<String> out = new ArrayList<>();
        for (String s : items) {
            if (s == null) continue;
            if (!p.isEmpty() && s.toUpperCase(java.util.Locale.ROOT).startsWith(p)) out.add(s);
        }
        return out;
    }

    // Local model names + direct imported model names (preserve case).
    private static List<String> modelNamesForBuffer(TransferDescription td) {
        if (td == null) return java.util.Collections.emptyList();
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Model m : td.getModelsFromLastFile()) {
            if (m.getName() != null) names.add(m.getName());
            Model[] imps = m.getImporting();
            if (imps != null) {
                for (Model imp : imps) {
                    if (imp != null && imp.getName() != null) names.add(imp.getName());
                }
            }
        }
        return new ArrayList<>(names);
    }

    // Resolve typed model to canonical case from TD (local + direct imports). Fallback: typed.
    private static String findCanonicalModelName(TransferDescription td, String typed) {
        String t = typed.toUpperCase(java.util.Locale.ROOT);
        for (Model m : td.getModelsFromLastFile()) {
            String n = m.getName();
            if (n != null && n.toUpperCase(java.util.Locale.ROOT).equals(t)) return n;
        }
        for (Model m : td.getModelsFromLastFile()) {
            Model[] imps = m.getImporting();
            if (imps == null) continue;
            for (Model imp : imps) {
                String n = (imp != null) ? imp.getName() : null;
                if (n != null && n.toUpperCase(java.util.Locale.ROOT).equals(t)) return n;
            }
        }
        return typed;
    }

    /* ============================ insertion =============================== */

    private static class KeywordCompletion extends SideKickCompletion {
        private final Buffer buffer;
        private final int start, end;
        private final List<String> items;

        KeywordCompletion(View view, Buffer buf, int s, int e, List<String> items) {
            super(view, "", items); // we replace the already-typed prefix
            this.buffer = buf;
            this.start  = s;
            this.end    = e;
            this.items  = items;
        }

        @Override
        public void insert(int index) {
            if (index < 0 || index >= items.size()) return;
            String replacement = items.get(index);
            buffer.remove(start, end - start);
            buffer.insert(start, replacement);
        }
    }
}
