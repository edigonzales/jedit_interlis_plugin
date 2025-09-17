package ch.so.agi.jedit.ui;

import java.util.ArrayList;
import java.util.Collections;
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.so.agi.jedit.InterlisAstUtil;
import ch.so.agi.jedit.ModelDiscoveryService;

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
        Log.log(Log.DEBUG, this, "SideKickParsedData.parse()");

        // 0. Create an (initially empty) tree. 
        final SideKickParsedData data = new SideKickParsedData(buffer.getName());
        final DefaultMutableTreeNode root = data.root;
                
        // 1. Try to get a ready TransferDescription from cache 
        // td ist null, falls der Buffer nicht im Cache ist oder
        // nicht mehr gültig, weil sich die Datei geändert hat (auf
        // dem Filesystem).
        TransferDescription td = TdCache.peek(buffer);
        Log.log(Log.DEBUG, this, "TransferDescription is stale or missing.");

        if (td != null) { // cached & up-to-date
            Log.log(Log.DEBUG, this, "TransferDescription is not null.");
            Log.log(Log.DEBUG, this, "outline / tree wird erstellt und anschliessend direkt returniert.");
            buildTree(buffer, td, root);
            return data; // full outline
        }
        
        // 2. Schedule compile in background if not cached (or stale).  
        Log.log(Log.DEBUG, this, "TransferDescription is null. Entweder nicht im Cache oder Datei hat sich geändert. Es wird der Cache erstellt.");
        TdCache.get(buffer).thenAccept(ast -> {
            if (ast == null) return; // syntax error
            Log.log(Log.DEBUG, this, "thenAccept");

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
        Buffer buf = editPane.getBuffer();

        // Use the most recently completed TD (even if buffer is dirty)
        TransferDescription td = TdCache.peekLastValid(buf);
        if (td == null) return null;

        int line = editPane.getTextArea().getCaretLine();
        int lineStart = buf.getLineStartOffset(line);
        int uptoCaret = Math.max(0, caret - lineStart);
        String lineText = buf.getText(lineStart, uptoCaret);
        
        
     // -------- IMPORTS clause context (now also on empty prefix) --------------
        ImportsInfo imp = findImportsContext(buf, caret);
        if (imp != null) {
            // Build query: empty prefix → list everything via "*", otherwise prefix+"*"
            String query = imp.prefix.isEmpty() ? "*" : (imp.prefix + "*");

            java.util.List<String> found = ModelDiscoveryService.searchModelsByName(query);
            if (found == null) found = Collections.emptyList();

            // Filter out models already listed in this IMPORTS clause (case-insensitive)
            java.util.HashSet<String> already = new java.util.HashSet<>();
            for (String a : imp.already) if (a != null)
                already.add(a.toUpperCase(java.util.Locale.ROOT));

            java.util.ArrayList<String> items = new java.util.ArrayList<>();
            for (String s : found) {
                if (s == null) continue;
                if (!already.contains(s.toUpperCase(java.util.Locale.ROOT))) items.add(s);
            }

            if (!items.isEmpty()) {
                items.sort(String.CASE_INSENSITIVE_ORDER);
                // Replace only the current token (may be empty) starting at imp.prefixStartAbs
                return new KeywordCompletion(editPane.getView(), buf, imp.prefixStartAbs, caret, items);
            }
            return null; // we're in IMPORTS context, but nothing to suggest
        }

        // -------- dotted path context:  Model.Topic.Class.<prefix> -------------
        String path = trailingPath(lineText);                 // e.g. "Model.Topic.Cl" or "Model.Topic."
        String[] parts = path.split("\\.", -1);               // PRESERVE trailing empty segment
        int lastDot = path.lastIndexOf('.');
        if (lastDot >= 0 && parts.length >= 2) {
            String prefix = parts[parts.length - 1];          // text after the last dot
            Log.log(Log.DEBUG, this, "prefix:" + prefix);
            if (prefix.isEmpty()) {
                // RULE: require at least one character after the dot
                //return null;

                // If you prefer to show all children on a trailing dot, comment the return above
                // and uncomment the next line to remember the last non-empty segment for filtering:
                //String lastNonEmpty = parts[parts.length - 2];
            }

            // Resolve chain up to the parent container (everything before the last segment)
            Object parent = resolveChain(td, parts, parts.length - 1);
            if (parent == null) return null;

            // Collect immediate children of that parent
            List<String> children = InterlisAstUtil.collectChildrenNames(parent);
            if (children.isEmpty()) return null;

            // Optional: if you enabled popup on empty prefix, filter out the last non-empty segment
            //children.removeIf(s -> s.equalsIgnoreCase(parts[parts.length - 2]));

          List<String> matches = null;
          if (prefix.length() ==  0) {
              matches = children;
          } else {
              matches = startsWithFilterCI(children, prefix);                        
          }

            if (matches.isEmpty()) return null;

            int start = caret - prefix.length();
            return new KeywordCompletion(editPane.getView(), buf, start, caret, matches);
        }
        
        // -------- plain word context: model names only (require ≥1 char) --------
        String word = currentWord(lineText);
        if (word.length() == 0) return null;

        List<String> modelNames = modelNamesForBuffer(td);
        List<String> matches = startsWithFilterCI(modelNames, word);
        if (matches.isEmpty()) return null;

        // de-dup (case-insensitive), preserve display case
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
    
    /** Return the trailing identifier/dot path right before the caret, e.g. "Model.Topic.Cl". */
    private static String trailingPath(String s) {
        int i = s.length() - 1;
        while (i >= 0) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '.') i--;
            else break;
        }
        return s.substring(i + 1); // may be empty
    }

    /** trailing word on the line (letters/digits/underscore) */
    private static String currentWord(String s) {
        int i = s.length() - 1;
        while (i >= 0) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') i--; else break;
        }
        return s.substring(i + 1);
    }

    /** Case-insensitive startsWith; returns original-cased items. Requires prefix length ≥ 1. */
    private static List<String> startsWithFilterCI(List<String> items, String prefix) {
        if (items == null || items.isEmpty() || prefix == null || prefix.isEmpty())
            return java.util.Collections.emptyList();
        final String p = prefix.toUpperCase(java.util.Locale.ROOT);
        ArrayList<String> out = new ArrayList<>();
        for (String s : items) {
            if (s == null) continue;
            if (s.toUpperCase(java.util.Locale.ROOT).startsWith(p)) out.add(s);
        }
        return out;
    }

    /** Local model names + direct imported model names (preserve case). */
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

    /* -------------------- resolution & children collection -------------------- */

    /** Resolve a dotted chain up to (but not including) parts[stop] and return that parent node. */
    private static Object resolveChain(TransferDescription td, String[] parts, int stop) {
        if (stop <= 0) return null;                  // needs Model . ...
        Model m = InterlisAstUtil.resolveModel(td, parts[0]);
        if (m == null) return null;
        Object cur = m;                              // can become Topic or Viewable as we descend

        for (int i = 1; i < stop; i++) {
            String name = parts[i];
            if (cur instanceof Model) {
                Element child = InterlisAstUtil.findChildInModelByName((Model)cur, name);
                if (child == null) return null;
                if (child instanceof Topic || child instanceof Viewable) {
                    cur = child;                     // descend
                } else {
                    return null; // can’t descend into domains/units/functions
                }
            }
            else if (cur instanceof Topic) {
                Element child = InterlisAstUtil.findChildInContainerByName((Topic)cur, name);
                if (child instanceof Viewable) cur = child; else return null;
            }
            else if (cur instanceof Viewable) {
                // next link would be attribute/role name; we only descend when we suggest attributes
                // i < stop implies there is something after; so require that name matches an attribute/role
                if (!InterlisAstUtil.hasAttributeOrRole((Viewable)cur, name)) return null;
                // Could descend further if you later want nested paths like a.b.c.attr.subattr
                cur = /* attribute level reached */ cur;
            }
            else {
                return null;
            }
        }
        return cur;
    }
    
    private static final Pattern IMPORTS_WORD = Pattern.compile("(?i)\\bIMPORTS\\b");

    /** Info for completion inside an IMPORTS clause. */
    private static final class ImportsInfo {
        final int prefixStartAbs;    // absolute offset of the token we’ll replace
        final String prefix;         // the currently typed token (trimmed)
        final List<String> already;  // other model names in this clause (before the current token)
        ImportsInfo(int start, String p, List<String> a) {
            this.prefixStartAbs = start; this.prefix = p; this.already = a;
        }
    }
    
    /**
     * If the caret is within an IMPORTS ... ; clause, returns the current token
     * to complete and the already-listed model names. Otherwise returns null.
     */
    private static ImportsInfo findImportsContext(Buffer buf, int caret) {
        // Look back a reasonable window (stop early at previous ';' if found)
        final int LOOKBACK = 2000;
        int start = Math.max(0, caret - LOOKBACK);
        String tail = buf.getText(start, caret - start);

        // Last "IMPORTS" word before the caret
        int impStart = -1, impEnd = -1;
        Matcher m = IMPORTS_WORD.matcher(tail);
        while (m.find()) { impStart = m.start(); impEnd = m.end(); }
        if (impStart < 0) return null;

        // If there is a ';' after IMPORTS (still in the tail), we’re past the clause → no context
        if (tail.indexOf(';', impStart) >= 0) return null;

        // Part between IMPORTS and caret
        String segment = tail.substring(impEnd); // may contain multiple names, commas, spaces

        // The current token starts after the last comma
        int lastComma = segment.lastIndexOf(',');
        int tokenRelStart = (lastComma >= 0) ? lastComma + 1 : 0;

        // Skip leading spaces in the token
        int p = tokenRelStart;
        while (p < segment.length() && Character.isWhitespace(segment.charAt(p))) p++;

        int prefixStartAbs = start + impEnd + p;
        String prefix = segment.substring(p).trim(); // token being typed

        // Collect already-listed model names (everything before the current token)
        ArrayList<String> already = new ArrayList<>();
        String before = segment.substring(0, p).trim(); // up to start of the token
        if (!before.isEmpty()) {
            String[] parts = before.split(",");
            for (String s : parts) {
                String name = s.trim();
                if (!name.isEmpty()) already.add(name);
            }
        }
        
        return new ImportsInfo(prefixStartAbs, prefix, already);
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
